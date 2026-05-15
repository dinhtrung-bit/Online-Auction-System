package server.services;

import server.dao.core.DBConnection;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.AutoBidDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.AutoBidConfig;
import server.models.auction.BidRecord;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.networks.interfaces.BroadcastChannel;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class AuctionService {
    private static AuctionService instance;

    private final AuctionRoomDAO roomDAO;
    private final ItemDAO itemDAO;
    private final BidMessageDAO bidDAO;
    private final UserDAO userDAO;
    private final AutoBidDAO autoBidDAO;
    private final BroadcastChannel broadcaster;

    private final ConcurrentHashMap<Long, AuctionRoom> activeRooms;

    private AuctionService(
            AuctionRoomDAO roomDAO,
            ItemDAO itemDAO,
            BidMessageDAO bidDAO,
            UserDAO userDAO,
            AutoBidDAO autoBidDAO,
            BroadcastChannel broadcaster
    ) {
        this.roomDAO = roomDAO;
        this.itemDAO = itemDAO;
        this.bidDAO = bidDAO;
        this.userDAO = userDAO;
        this.autoBidDAO = autoBidDAO;
        this.broadcaster = broadcaster;
        this.activeRooms = new ConcurrentHashMap<>();
        loadRoomsFromDatabase();
    }

    public BroadcastChannel getBroadcaster() {
        return broadcaster;
    }

    public static synchronized AuctionService getInstance(
            AuctionRoomDAO roomDAO,
            ItemDAO itemDAO,
            BidMessageDAO bidDAO,
            UserDAO userDAO,
            AutoBidDAO autoBidDAO,
            BroadcastChannel broadcaster
    ) {
        if (instance == null) {
            instance = new AuctionService(roomDAO, itemDAO, bidDAO, userDAO, autoBidDAO, broadcaster);
        }
        return instance;
    }

    public static AuctionService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AuctionService chưa được khởi tạo.");
        }
        return instance;
    }

    private void loadRoomsFromDatabase() {
        try {
            List<AuctionRoom> rooms = roomDAO.findAll();
            activeRooms.clear();
            for (AuctionRoom room : rooms) {
                activeRooms.put((long) room.getId(), room);
            }
            System.out.println(">>> [AuctionService] Đã nạp dữ liệu auction từ DB.");
        } catch (Exception e) {
            System.err.println(">>> [AuctionService] Lỗi nạp dữ liệu: " + e.getMessage());
        }
    }

    public void reloadFromDatabase() {
        loadRoomsFromDatabase();
    }

    public List<AuctionRoom> getActiveRooms() {
        return new ArrayList<>(activeRooms.values());
    }

    public AuctionRoom findRoomById(long roomId) {
        return activeRooms.get(roomId);
    }

    public void createAuction(int sellerId, Item item, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        if (item == null) {
            throw new IllegalArgumentException("Sản phẩm không được null.");
        }

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian đấu giá không hợp lệ.");
        }

        AuctionRoom room = new AuctionRoom(0, sellerId, item, startTime, endTime);
        room.setStatus(AuctionStatus.OPEN);
        room.setCurrentPrice(item.getStartingPrice());

        roomDAO.insert(room);
        reloadFromDatabase();
    }

    public String handleBidRequest(Long roomId, Bidder bidder, double amount) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return "Không tìm thấy phòng đấu giá.";
        if (bidder == null) return "Người đặt giá không hợp lệ.";

        BigDecimal bidAmount = BigDecimal.valueOf(amount);
        if (bidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Giá đặt phải lớn hơn 0.";
        }

        synchronized (room) {
            try {
                if (room.getSellerID() == bidder.getUserId()) {
                    return "Seller không được tự bid sản phẩm của mình.";
                }

                if (room.getStatus() != AuctionStatus.RUNNING) {
                    return "Phiên đấu giá chưa chạy hoặc đã kết thúc.";
                }

                User freshUser = userDAO.findById(bidder.getUserId());
                if (!(freshUser instanceof Bidder freshBidder)) {
                    return "Tài khoản không phải Bidder.";
                }

                if (freshBidder.getAccountBalance().compareTo(bidAmount) < 0) {
                    return "Số dư không đủ.";
                }

                BigDecimal oldPrice = room.getCurrentPrice();

                room.placeBid(freshBidder, bidAmount);

                roomDAO.updateWithOptimisticLock(room, oldPrice);
                bidDAO.insert(new BidRecord(roomId.intValue(), freshBidder.getUserId(), bidAmount));

                processAutoBids(room, freshBidder, 0);

                System.out.println(">>> [Bid] Room " + roomId + ": " + freshBidder.getUsername() + " bid " + bidAmount);
                return "SUCCESS";

            } catch (Exception e) {
                return e.getMessage();
            }
        }
    }

    public void triggerAutoBidsForRoom(long roomId, Bidder trigger) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return;

        synchronized (room) {
            processAutoBids(room, trigger, 0);
        }
    }

    private void processAutoBids(AuctionRoom room, Bidder lastBidder, int depth) {
        if (depth > 20) return;

        try {
            List<AutoBidConfig> autoBids = autoBidDAO.getAutoBidsByAuctionId(room.getId());
            if (autoBids == null || autoBids.isEmpty()) return;

            for (AutoBidConfig config : autoBids) {
                Bidder autoBidder = config.getBidder();
                if (autoBidder == null) continue;

                if (lastBidder != null && autoBidder.getUserId() == lastBidder.getUserId()) continue;
                if (room.getCurrentWinner() != null
                        && room.getCurrentWinner().getUserId() == autoBidder.getUserId()) continue;
                if (room.getSellerID() == autoBidder.getUserId()) continue;

                BigDecimal increment = config.getIncrement() != null
                        ? config.getIncrement()
                        : BigDecimal.ONE;

                BigDecimal nextBid = room.getCurrentPrice().add(increment);

                if (nextBid.compareTo(config.getMaxBid()) > 0) {
                    broadcaster.broadcast(new com.google.gson.Gson().toJson(
                            new MessageDTO("AUTO_BID_EXCEEDED", String.valueOf(room.getId()))
                    ));
                    continue;
                }

                User fullUser = userDAO.findById(autoBidder.getUserId());
                if (!(fullUser instanceof Bidder fullBidder)) continue;

                if (fullBidder.getAccountBalance().compareTo(nextBid) < 0) continue;

                BigDecimal oldPrice = room.getCurrentPrice();

                room.placeAutoBid(fullBidder, nextBid);

                roomDAO.updateWithOptimisticLock(room, oldPrice);
                bidDAO.insert(new BidRecord(room.getId(), fullBidder.getUserId(), nextBid));

                broadcaster.broadcast(new com.google.gson.Gson().toJson(
                        new MessageDTO(
                                "UPDATE_PRICE",
                                room.getId() + ":" + nextBid.toPlainString() + ":" + fullBidder.getUsername()
                        )
                ));

                System.out.println(">>> [AutoBid] " + fullBidder.getUsername()
                        + " bid " + nextBid + " vào phòng " + room.getId());

                processAutoBids(room, fullBidder, depth + 1);
                break;
            }

        } catch (Exception e) {
            System.err.println(">>> [AutoBid Error] " + e.getMessage());
        }
    }

    public void autoUpdateStatuses() {
        LocalDateTime now = LocalDateTime.now();

        for (AuctionRoom room : activeRooms.values()) {
            synchronized (room) {
                try {
                    if (room.getStatus() == AuctionStatus.FINISHED
                            || room.getStatus() == AuctionStatus.PAID
                            || room.getStatus() == AuctionStatus.CANCELED) {
                        continue;
                    }

                    if (room.getStatus() == AuctionStatus.OPEN && !now.isBefore(room.getStarttime())) {
                        room.setStatus(AuctionStatus.RUNNING);
                        updateRoomInDB(room);

                        broadcaster.broadcast(new com.google.gson.Gson().toJson(
                                new MessageDTO("AUCTION_STARTED", String.valueOf(room.getId()))
                        ));

                        System.out.println(">>> [Auction] Room " + room.getId() + " START.");
                    }

                    if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                        processAuctionSettlement(room);
                        updateRoomInDB(room);

                        broadcaster.broadcast(new com.google.gson.Gson().toJson(
                                new MessageDTO("AUCTION_FINISHED", String.valueOf(room.getId()))
                        ));

                        scheduleRemoveRoom(room.getId());
                    }

                } catch (Exception e) {
                    System.err.println(">>> [Auction Status Error] " + e.getMessage());
                }
            }
        }
    }

    private void processAuctionSettlement(AuctionRoom room) {
        User winner = room.getCurrentWinner();
        BigDecimal finalPrice = room.getCurrentPrice();

        if (winner == null || finalPrice == null || finalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            room.setStatus(AuctionStatus.CANCELED);
            System.out.println(">>> [Settlement] Room " + room.getId() + " CANCELED: Không có người mua.");
            return;
        }

        try (Connection conn = DBConnection.getInstance()) {
            conn.setAutoCommit(false);

            try {
                User freshWinner = userDAO.findById(winner.getUserId());
                User freshSeller = userDAO.findById(room.getSellerID());

                if (freshWinner == null || freshSeller == null) {
                    throw new IllegalArgumentException("Không tìm thấy winner hoặc seller.");
                }

                if (freshWinner.getAccountBalance().compareTo(finalPrice) < 0) {
                    room.setStatus(AuctionStatus.CANCELED);
                    roomDAO.update(room);
                    conn.commit();

                    System.out.println(">>> [Settlement] Room " + room.getId()
                            + " CANCELED: Winner không đủ số dư.");
                    return;
                }

                subtractBalance(conn, freshWinner.getUserId(), finalPrice);
                addBalance(conn, freshSeller.getUserId(), finalPrice);

                room.setStatus(AuctionStatus.PAID);
                roomDAO.update(room);

                conn.commit();

                System.out.println(">>> [Settlement] Room " + room.getId()
                        + " PAID: " + freshWinner.getUsername() + " trả " + finalPrice);

            } catch (Exception e) {
                conn.rollback();
                room.setStatus(AuctionStatus.CANCELED);
                try {
                    roomDAO.update(room);
                } catch (Exception ignored) {
                }
                throw e;

            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            room.setStatus(AuctionStatus.CANCELED);
            System.err.println(">>> [Settlement Error] " + e.getMessage());
        }
    }

    private void subtractBalance(Connection conn, int userId, BigDecimal amount) throws Exception {
        String sql = "UPDATE users SET balance = balance - ? WHERE user_id = ? AND balance >= ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, userId);
            ps.setBigDecimal(3, amount);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("Số dư không đủ để thanh toán.");
            }
        }
    }

    private void addBalance(Connection conn, int userId, BigDecimal amount) throws Exception {
        String sql = "UPDATE users SET balance = COALESCE(balance, 0) + ? WHERE user_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, userId);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("Không tìm thấy seller để cộng tiền.");
            }
        }
    }

    private void updateRoomInDB(AuctionRoom room) {
        BigDecimal oldPrice = room.getCurrentPrice();

        try {
            roomDAO.updateWithOptimisticLock(room, oldPrice);
        } catch (Exception e) {
            try {
                roomDAO.update(room);
            } catch (Exception ignored) {
            }
            System.err.println(">>> [DB Update Room Error] " + e.getMessage());
        }
    }

    private void scheduleRemoveRoom(long roomId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(30_000);
                activeRooms.remove(roomId);
                System.out.println(">>> [Auction] Đã xóa Room " + roomId + " khỏi RAM.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public String cancelAuctionBySeller(int auctionId, int sellerId) throws Exception {
        AuctionRoom room = activeRooms.get((long) auctionId);

        if (room == null) return "Không tìm thấy phiên đấu giá.";

        synchronized (room) {
            if (room.getSellerID() != sellerId) return "Bạn không có quyền hủy phiên đấu giá này.";

            if (room.getStatus() == AuctionStatus.PAID || room.getStatus() == AuctionStatus.FINISHED) {
                return "Phiên đã kết thúc hoặc đã thanh toán, không thể hủy.";
            }

            if (room.getStatus() == AuctionStatus.RUNNING && room.getCurrentWinner() != null) {
                return "Phiên đang chạy đã có người đặt giá, không thể hủy.";
            }

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
            if (room.getStatus() == AuctionStatus.PAID) {
                return "Phiên đã thanh toán, không thể hủy.";
            }

            room.setStatus(AuctionStatus.CANCELED);
            roomDAO.update(room);
            reloadFromDatabase();

            return "SUCCESS";
        }
    }

    public void registerAutoBid(int auctionId, Bidder bidder, BigDecimal maxBid, BigDecimal step) throws Exception {
        if (bidder == null) throw new IllegalArgumentException("Bidder không hợp lệ.");
        if (maxBid == null || maxBid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá tối đa phải lớn hơn 0.");
        }
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bước nhảy phải lớn hơn 0.");
        }

        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá.");

        if (room.getSellerID() == bidder.getUserId()) {
            throw new IllegalArgumentException("Seller không được bật auto bid cho sản phẩm của mình.");
        }

        AutoBidConfig config = new AutoBidConfig(auctionId, bidder, maxBid, step);
        autoBidDAO.insert(config);

        triggerAutoBidsForRoom(auctionId, bidder);
    }

    public void cancelAutoBid(int auctionId, int bidderId) throws Exception {
        autoBidDAO.deleteByAuctionIdAndBidderId(auctionId, bidderId);
    }

    public List<java.util.Map<String, Object>> getBidHistory(int roomId) throws Exception {
        List<BidRecord> bids = bidDAO.getBidHistoryByAuctionRoomId(roomId);
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

        for (BidRecord b : bids) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            String username = "Người dùng #" + b.getBidderId();

            try {
                User u = userDAO.findById(b.getBidderId());
                if (u != null) username = u.getUsername();
            } catch (Exception ignored) {
            }

            m.put("username", username);
            m.put("amount", b.getBidAmount().doubleValue());
            m.put("time", b.getTimestamp() != null ? b.getTimestamp().toString() : "");
            result.add(m);
        }

        return result;
    }
}