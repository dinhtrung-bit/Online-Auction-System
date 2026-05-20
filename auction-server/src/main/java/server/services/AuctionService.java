package server.services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

import server.dao.core.DBConnection;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.AutoBidDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.UserDAO;
import server.exceptions.InvalidBidException;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.AutoBidConfig;
import server.models.auction.BidRecord;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.networks.interfaces.BroadcastChannel;
import server.utils.Validation;

/**
 * AuctionService — Singleton chứa toàn bộ logic nghiệp vụ đấu giá.
 *
 * Thay đổi so với phiên bản cũ:
 *
 * 1. handleBidRequest() không còn nhận double — nhận BigDecimal để tránh mất độ chính xác.
 *    Handler chuyển đổi từ payload trước khi gọi.
 *
 * 2. Business rules đã được dời từ handler vào đây:
 *    - Seller không được tự bid sản phẩm của mình.
 *    - Phiên phải đang RUNNING.
 *    - Bidder phải có đủ số dư (freshBidder.hasEnoughBalance()).
 *
 * 3. createAuction() nhận thêm tham số sellerId và item để tự kiểm tra ownership —
 *    handler chỉ truyền data thô, không tự validate ownership nữa.
 *
 * 4. processRoomStatusTick() dùng Validation.canTransitionTo() thay vì hard-code điều kiện.
 *
 * 5. processAuctionSettlement() dùng User.debit() / User.credit() thay vì SQL thô —
 *    nhất quán với domain model, logic trừ/cộng tiền chỉ có một nơi.
 */
public class AuctionService {

    private static final int AUTO_BID_MAX_DEPTH  = 20;
    private static final int ROOM_REMOVE_DELAY_MS = 30_000;
    private static final Gson GSON = new Gson();

    private static AuctionService instance;

    private final AuctionRoomDAO roomDAO;
    private final ItemDAO        itemDAO;
    private final BidMessageDAO  bidDAO;
    private final UserDAO        userDAO;
    private final AutoBidDAO     autoBidDAO;
    private final BroadcastChannel broadcaster;

    private final ConcurrentHashMap<Long, AuctionRoom> activeRooms = new ConcurrentHashMap<>();

    private AuctionService(AuctionRoomDAO roomDAO, ItemDAO itemDAO, BidMessageDAO bidDAO,
                           UserDAO userDAO, AutoBidDAO autoBidDAO, BroadcastChannel broadcaster) {
        this.roomDAO     = roomDAO;
        this.itemDAO     = itemDAO;
        this.bidDAO      = bidDAO;
        this.userDAO     = userDAO;
        this.autoBidDAO  = autoBidDAO;
        this.broadcaster = broadcaster;
        loadRoomsFromDatabase();
    }

    public static synchronized AuctionService getInstance(
            AuctionRoomDAO roomDAO, ItemDAO itemDAO, BidMessageDAO bidDAO,
            UserDAO userDAO, AutoBidDAO autoBidDAO, BroadcastChannel broadcaster) {
        if (instance == null) {
            instance = new AuctionService(roomDAO, itemDAO, bidDAO, userDAO, autoBidDAO, broadcaster);
        }
        return instance;
    }

    public static AuctionService getInstance() {
        if (instance == null) throw new IllegalStateException("AuctionService chưa được khởi tạo.");
        return instance;
    }

    public BroadcastChannel getBroadcaster() { return broadcaster; }

    // ── Room loading & queries ───────────────────────────────────────────────

    private void loadRoomsFromDatabase() {
        try {
            activeRooms.clear();
            for (AuctionRoom room : roomDAO.findAll()) {
                activeRooms.put((long) room.getId(), room);
            }
            System.out.println(">>> [AuctionService] Đã nạp dữ liệu auction từ DB.");
        } catch (Exception e) {
            System.err.println(">>> [AuctionService] Lỗi nạp dữ liệu: " + e.getMessage());
        }
    }

    public void reloadFromDatabase()                    { loadRoomsFromDatabase(); }
    public List<AuctionRoom> getActiveRooms()           { return new ArrayList<>(activeRooms.values()); }
    public AuctionRoom findRoomById(long roomId)        { return activeRooms.get(roomId); }

    // ── Create / Cancel ──────────────────────────────────────────────────────

    /**
     * Tạo phiên đấu giá mới.
     *
     * Business rules được kiểm tra ở đây (dời từ AuctionRequestHandler):
     *   - item phải tồn tại.
     *   - seller phải sở hữu item.
     *   - startTime không được trong quá khứ.
     *   - endTime phải sau startTime.
     */
    public void createAuction(int sellerId, Item item, LocalDateTime startTime, LocalDateTime endTime)
            throws Exception {
        if (item == null) {
            throw new IllegalArgumentException("Sản phẩm không được null.");
        }
        if (item.getSeller() == null || item.getSeller().getUserId() != sellerId) {
            throw new IllegalArgumentException("Bạn không sở hữu sản phẩm này.");
        }
        if (startTime == null || startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Không thể tạo phiên đấu giá trong quá khứ.");
        }
        if (endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }

        AuctionRoom room = new AuctionRoom(0, sellerId, item, startTime, endTime);
        room.setStatus(AuctionStatus.OPEN);
        room.setCurrentPrice(item.getStartingPrice());

        roomDAO.insert(room);
        reloadFromDatabase();
    }

    public String cancelAuctionBySeller(int auctionId, int sellerId) throws Exception {
        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null) return "Không tìm thấy phiên đấu giá.";

        synchronized (room) {
            if (room.getSellerID() != sellerId)
                return "Bạn không có quyền hủy phiên đấu giá này.";
            if (room.getStatus() == AuctionStatus.PAID || room.getStatus() == AuctionStatus.FINISHED)
                return "Phiên đã kết thúc hoặc đã thanh toán, không thể hủy.";
            if (room.getStatus() == AuctionStatus.RUNNING && room.getCurrentWinner() != null)
                return "Phiên đang chạy đã có người đặt giá, không thể hủy.";

            room.setStatus(AuctionStatus.CANCELED);
            roomDAO.update(room);
            reloadFromDatabase();
            return "SUCCESS";
        }
    }

    public String cancelAuctionByAdmin(int auctionId) throws Exception {
        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null) return "Không tìm thấy phiên đấu giá.";

        synchronized (room) {
            if (room.getStatus() == AuctionStatus.PAID)
                return "Phiên đã thanh toán, không thể hủy.";

            room.setStatus(AuctionStatus.CANCELED);
            roomDAO.update(room);
            reloadFromDatabase();
            return "SUCCESS";
        }
    }

    // ── Bid ──────────────────────────────────────────────────────────────────

    /**
     * Xử lý một lệnh bid thủ công.
     *
     * Business rules được kiểm tra ở đây (không còn nằm trong AuctionRequestHandler):
     *   - Seller không tự bid sản phẩm của mình.
     *   - Phiên phải RUNNING.
     *   - Bidder phải có đủ số dư (lấy freshBidder từ DB để chống race condition).
     *
     * @param roomId    ID phòng đấu giá
     * @param bidder    Bidder đã được xác thực từ session
     * @param bidAmount Số tiền đặt (đã convert từ payload ở handler)
     * @return "SUCCESS" hoặc thông báo lỗi
     */
    public String handleBidRequest(Long roomId, Bidder bidder, BigDecimal bidAmount) {
        if (bidAmount == null || bidAmount.compareTo(BigDecimal.ZERO) <= 0)
            return "Giá đặt phải lớn hơn 0.";

        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return "Không tìm thấy phòng đấu giá.";
        if (bidder == null) return "Người đặt giá không hợp lệ.";

        synchronized (room) {
            try {
                if (room.getSellerID() == bidder.getUserId())
                    return "Seller không được tự bid sản phẩm của mình.";
                if (room.getStatus() != AuctionStatus.RUNNING)
                    return "Phiên đấu giá chưa chạy hoặc đã kết thúc.";

                // Lấy fresh user từ DB để số dư chính xác nhất
                User fresh = userDAO.findById(bidder.getUserId());
                if (!(fresh instanceof Bidder freshBidder))
                    return "Tài khoản không phải Bidder.";
                if (!freshBidder.hasEnoughBalance(bidAmount))
                    return "Số dư không đủ (hiện có: " + freshBidder.getAccountBalance().toPlainString() + " đ).";

                BigDecimal oldPrice = room.getCurrentPrice();
                room.placeBid(freshBidder, bidAmount);  // domain invariant check bên trong
                roomDAO.updateWithOptimisticLock(room, oldPrice);
                bidDAO.insert(new BidRecord(roomId.intValue(), freshBidder.getUserId(), bidAmount));

                processAutoBids(room, freshBidder, 0);

                System.out.printf(">>> [Bid] Room %d: %s bid %s%n",
                        roomId, freshBidder.getUsername(), bidAmount.toPlainString());
                return "SUCCESS";

            } catch (InvalidBidException e) {
                return e.getMessage();
            } catch (Exception e) {
                System.err.println(">>> [Bid Error] " + e.getMessage());
                return "Lỗi xử lý đặt giá: " + e.getMessage();
            }
        }
    }

    // ── Auto-bid ─────────────────────────────────────────────────────────────

    public void registerAutoBid(int auctionId, Bidder bidder, BigDecimal maxBid, BigDecimal step)
            throws Exception {
        if (bidder == null)
            throw new IllegalArgumentException("Bidder không hợp lệ.");
        if (maxBid == null || maxBid.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Giá tối đa phải lớn hơn 0.");
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Bước nhảy phải lớn hơn 0.");

        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null)
            throw new IllegalArgumentException("Không tìm thấy phiên đấu giá.");
        if (room.getSellerID() == bidder.getUserId())
            throw new IllegalArgumentException("Seller không được bật auto-bid cho sản phẩm của mình.");
        if (!bidder.hasEnoughBalance(maxBid))
            throw new IllegalArgumentException("Số dư không đủ để bảo lãnh mức Max Bid này.");

        autoBidDAO.insert(new AutoBidConfig(auctionId, bidder, maxBid, step));
        triggerAutoBidsForRoom(auctionId, bidder);
    }

    public void cancelAutoBid(int auctionId, int bidderId) throws Exception {
        autoBidDAO.deleteByAuctionIdAndBidderId(auctionId, bidderId);
    }

    public void triggerAutoBidsForRoom(long roomId, Bidder trigger) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return;
        synchronized (room) { processAutoBids(room, trigger, 0); }
    }

    private void processAutoBids(AuctionRoom room, Bidder lastBidder, int depth) {
        if (depth > AUTO_BID_MAX_DEPTH) return;

        try {
            List<AutoBidConfig> autoBids = autoBidDAO.getAutoBidsByAuctionId(room.getId());
            if (autoBids == null || autoBids.isEmpty()) return;

            for (AutoBidConfig config : autoBids) {
                if (processSingleAutoBid(room, lastBidder, config, depth)) return;
            }
        } catch (Exception e) {
            System.err.println(">>> [AutoBid Error] " + e.getMessage());
        }
    }

    private boolean processSingleAutoBid(AuctionRoom room, Bidder lastBidder,
                                         AutoBidConfig config, int depth) throws Exception {
        Bidder autoBidder = config.getBidder();
        if (autoBidder == null) return false;
        if (lastBidder != null && autoBidder.getUserId() == lastBidder.getUserId()) return false;
        if (room.getCurrentWinner() != null &&
                room.getCurrentWinner().getUserId() == autoBidder.getUserId()) return false;
        if (room.getSellerID() == autoBidder.getUserId()) return false;

        BigDecimal increment = config.getIncrement() != null ? config.getIncrement() : BigDecimal.ONE;
        BigDecimal nextBid   = room.getCurrentPrice().add(increment);

        if (nextBid.compareTo(config.getMaxBid()) > 0) {
            broadcast("AUTO_BID_EXCEEDED", String.valueOf(room.getId()));
            return false;
        }

        User full = userDAO.findById(autoBidder.getUserId());
        if (!(full instanceof Bidder freshBidder)) return false;
        if (!freshBidder.hasEnoughBalance(nextBid)) return false;

        BigDecimal oldPrice = room.getCurrentPrice();
        room.applyAutoBid(freshBidder, nextBid);  // dùng method mới, không phải placeAutoBid cũ
        roomDAO.updateWithOptimisticLock(room, oldPrice);
        bidDAO.insert(new BidRecord(room.getId(), freshBidder.getUserId(), nextBid));

        broadcast("UPDATE_PRICE", room.getId() + ":" + nextBid.toPlainString() + ":" + freshBidder.getUsername());

        System.out.printf(">>> [AutoBid] %s bid %s vào phòng %d%n",
                freshBidder.getUsername(), nextBid.toPlainString(), room.getId());

        processAutoBids(room, freshBidder, depth + 1);
        return true;
    }

    // ── Status scheduler & settlement ────────────────────────────────────────

    public void autoUpdateStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (AuctionRoom room : activeRooms.values()) {
            synchronized (room) {
                try { processRoomStatusTick(room, now); }
                catch (Exception e) { System.err.println(">>> [Status Error] " + e.getMessage()); }
            }
        }
    }

    /**
     * Xử lý một tick trạng thái phòng.
     * Dùng Validation.canTransitionTo() để tập trung state machine logic.
     */
    private void processRoomStatusTick(AuctionRoom room, LocalDateTime now) {
        AuctionStatus current = room.getStatus();

        // Terminal states — không làm gì
        if (current == AuctionStatus.FINISHED ||
                current == AuctionStatus.PAID     ||
                current == AuctionStatus.CANCELED) return;

        // OPEN → RUNNING
        if (current == AuctionStatus.OPEN
                && !now.isBefore(room.getStarttime())
                && Validation.canTransitionTo(current, AuctionStatus.RUNNING)) {

            room.setStatus(AuctionStatus.RUNNING);
            updateRoomSafe(room);
            broadcast("AUCTION_STARTED", String.valueOf(room.getId()));
            System.out.println(">>> [Auction] Room " + room.getId() + " STARTED.");
        }

        // RUNNING → FINISHED (+ settlement)
        if (room.getStatus() == AuctionStatus.RUNNING
                && room.isExpired()
                && Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.FINISHED)) {

            room.setStatus(AuctionStatus.FINISHED);
            processAuctionSettlement(room);
            updateRoomSafe(room);
            broadcast("AUCTION_FINISHED", String.valueOf(room.getId()));
            scheduleRemoveRoom(room.getId());
        }
    }

    /**
     * Settlement: trừ tiền winner, cộng tiền seller trong một DB transaction.
     * Dùng User.debit() / User.credit() để logic tiền tệ nhất quán với domain model.
     */
    private void processAuctionSettlement(AuctionRoom room) {
        User winner     = room.getCurrentWinner();
        BigDecimal price = room.getCurrentPrice();

        if (winner == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            room.setStatus(AuctionStatus.CANCELED);
            System.out.println(">>> [Settlement] Room " + room.getId() + " CANCELED: Không có người mua.");
            return;
        }

        try (Connection conn = DBConnection.getInstance()) {
            conn.setAutoCommit(false);
            try {
                User freshWinner = userDAO.findById(winner.getUserId());
                User freshSeller = userDAO.findById(room.getSellerID());

                if (freshWinner == null || freshSeller == null)
                    throw new IllegalStateException("Không tìm thấy winner hoặc seller.");

                // Dùng domain method — logic trừ tiền tập trung tại User.debit()
                if (!freshWinner.debit(price)) {
                    room.setStatus(AuctionStatus.CANCELED);
                    roomDAO.update(room);
                    conn.commit();
                    System.out.println(">>> [Settlement] Room " + room.getId()
                            + " CANCELED: Winner không đủ số dư.");
                    return;
                }

                freshSeller.credit(price);

                userDAO.update(freshWinner);
                userDAO.update(freshSeller);
                room.setStatus(AuctionStatus.PAID);
                roomDAO.update(room);
                conn.commit();

                System.out.printf(">>> [Settlement] Room %d PAID: %s trả %s%n",
                        room.getId(), freshWinner.getUsername(), price.toPlainString());

            } catch (Exception e) {
                conn.rollback();
                room.setStatus(AuctionStatus.CANCELED);
                try { roomDAO.update(room); } catch (Exception ignored) {}
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            room.setStatus(AuctionStatus.CANCELED);
            System.err.println(">>> [Settlement Error] " + e.getMessage());
        }
    }

    private void updateRoomSafe(AuctionRoom room) {
        try {
            roomDAO.updateWithOptimisticLock(room, room.getCurrentPrice());
        } catch (Exception e) {
            try { roomDAO.update(room); } catch (Exception ignored) {}
            System.err.println(">>> [DB Room Update] " + e.getMessage());
        }
    }

    private void scheduleRemoveRoom(long roomId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(ROOM_REMOVE_DELAY_MS);
                activeRooms.remove(roomId);
                System.out.println(">>> [Auction] Room " + roomId + " removed from RAM.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // ── Bid history ──────────────────────────────────────────────────────────

    public List<Map<String, Object>> getBidHistory(int roomId) throws Exception {
        List<BidRecord> bids = bidDAO.getBidHistoryByAuctionRoomId(roomId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (BidRecord b : bids) {
            String username = "Người dùng #" + b.getBidderId();
            try {
                User u = userDAO.findById(b.getBidderId());
                if (u != null) username = u.getUsername();
            } catch (Exception ignored) {}

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", username);
            m.put("amount",   b.getBidAmount().doubleValue());
            m.put("time",     b.getTimestamp() != null ? b.getTimestamp().toString() : "");
            result.add(m);
        }
        return result;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void broadcast(String action, String payload) {
        broadcaster.broadcast(GSON.toJson(new MessageDTO(action, payload)));
    }
}
