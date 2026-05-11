package server.services;

import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.AutoBidDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.AutoBidConfig;
import server.models.auction.BidMessage;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.User;
import server.networks.ClientHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class AuctionService {
    private static AuctionService instance;

    private final AuctionRoomDAO roomDAO;
    private final ItemDAO         itemDAO;
    private final BidMessageDAO   bidDAO;
    private final UserDAO         userDAO;
    private final AutoBidDAO      autoBidDAO;

    private ConcurrentHashMap<Long, AuctionRoom> activeRooms;

    /**
     * Constructor Injection — MainServer truyền DAO vào.
     * Singleton vẫn được giữ để scheduler và ClientHandler dùng chung 1 instance.
     */
    private AuctionService(AuctionRoomDAO roomDAO, ItemDAO itemDAO,
                           BidMessageDAO bidDAO, UserDAO userDAO, AutoBidDAO autoBidDAO) {
        this.roomDAO    = roomDAO;
        this.itemDAO    = itemDAO;
        this.bidDAO     = bidDAO;
        this.userDAO    = userDAO;
        this.autoBidDAO = autoBidDAO;
        this.activeRooms = new ConcurrentHashMap<>();
        loadRoomsFromDatabase();
    }

    /**
     * Lấy (hoặc tạo) instance duy nhất với DAO được inject từ MainServer.
     * Gọi lần đầu tại MainServer (Composition Root) để wiring đúng.
     */
    public static synchronized AuctionService getInstance(
            AuctionRoomDAO roomDAO, ItemDAO itemDAO,
            BidMessageDAO bidDAO, UserDAO userDAO, AutoBidDAO autoBidDAO) {
        if (instance == null) {
            instance = new AuctionService(roomDAO, itemDAO, bidDAO, userDAO, autoBidDAO);
        }
        return instance;
    }

    /**
     * Lấy instance đã được khởi tạo (dùng trong các class nội bộ nếu cần).
     * Yêu cầu: getInstance(DAOs...) phải được gọi trước tại MainServer.
     */
    public static AuctionService getInstance() {
        if (instance == null) throw new IllegalStateException(
                "AuctionService chưa được khởi tạo — gọi getInstance(DAOs...) tại MainServer trước.");
        return instance;
    }

    private void loadRoomsFromDatabase() {
        try {
            List<AuctionRoom> rooms = roomDAO.findAll();
            rooms.forEach(r -> activeRooms.put((long) r.getId(), r));
            System.out.println(">>> [Manager] Đã nạp dữ liệu từ Database vào RAM.");
        } catch (Exception e) {
            System.err.println("Lỗi nạp dữ liệu: " + e.getMessage());
        }
    }

    public void reloadFromDatabase() {
        try {
            List<AuctionRoom> rooms = roomDAO.findAll();
            rooms.forEach(r -> activeRooms.put((long) r.getId(), r));
            System.out.println(">>> [Manager] Đã reload dữ liệu từ DB vào RAM.");
        } catch (Exception e) {
            System.err.println("Lỗi reload: " + e.getMessage());
        }
    }

    public void createNewAuction(int sellerID, Item item, LocalDateTime endTime) {
        Long roomId = System.currentTimeMillis();
        AuctionRoom newRoom = new AuctionRoom(roomId.intValue(), sellerID, item, LocalDateTime.now(), endTime);
        activeRooms.put(roomId, newRoom);

        CompletableFuture.runAsync(() -> {
            try {
                itemDAO.insert(item);
                System.out.println(">>> [Manager] Đã lưu phiên đấu giá mới vào DB.");
            } catch (Exception e) {
                System.err.println("Lỗi lưu DB: " + e.getMessage());
            }
        });
    }

    public String handleBidRequest(Long roomId, Bidder bidder, double amount) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return "LỖI: Không tìm thấy phòng đấu giá!";

        BigDecimal bidAmount = BigDecimal.valueOf(amount);
        BigDecimal oldPrice;

        synchronized (room) {
            try {
                oldPrice = room.getCurrentPrice();
                room.placeBid(bidder, bidAmount);
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.updateWithOptimisticLock(room, oldPrice);

                BidMessage bid = new BidMessage(0, bidder.getUserId(), roomId.intValue(), bidAmount);
                bidDAO.insert(bid);

                System.out.println(">>> [DB Success] Phòng " + roomId + ": " + bidder.getUsername() + " bid " + amount);

                // Xử lý auto bid sau khi có người bid thủ công
                processAutoBids(room, bidder);

            } catch (Exception e) {
                System.err.println(">>> [DB Error] Lỗi lưu lịch sử đặt giá: " + e.getMessage());
            }
        });

        return "SUCCESS";
    }

    /**
     * Public API: kích hoạt vòng auto-bid ngay lập tức cho 1 phòng.
     * Dùng khi user vừa SET_AUTO_BID xong — để bot tự cạnh tranh ngay
     * thay vì phải đợi có người bid thủ công.
     *
     * @param roomId  id phòng cần kích hoạt
     * @param trigger người vừa thiết lập auto-bid (để bỏ qua chính họ trong vòng đầu)
     */
    public void triggerAutoBidsForRoom(long roomId, Bidder trigger) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return;
        processAutoBids(room, trigger);
    }

    private void processAutoBids(AuctionRoom room, Bidder lastBidder) {
        try {
            List<AutoBidConfig> autoBids = autoBidDAO.getAutoBidsByAuctionId(room.getId());
            if (autoBids == null || autoBids.isEmpty()) return;

            for (AutoBidConfig config : autoBids) {
                Bidder autoBidder = config.getBidder();

                // Bỏ qua nếu chính người vừa bid hoặc đang dẫn đầu
                if (autoBidder.getUserId() == lastBidder.getUserId()) continue;
                if (room.getCurrentWinner() != null &&
                        room.getCurrentWinner().getUserId() == autoBidder.getUserId()) continue;

                BigDecimal nextBid = room.getCurrentPrice().add(config.getIncrement());

                // Vượt quá giới hạn — báo client
                if (nextBid.compareTo(config.getMaxBid()) > 0) {
                    ClientHandler.broadcast(new com.google.gson.Gson().toJson(
                            new server.networks.dto.MessageDTO("AUTO_BID_EXCEEDED",
                                    String.valueOf(room.getId()))
                    ));
                    continue;
                }

                // Lấy thông tin đầy đủ của bidder từ DB
                User fullUser = userDAO.findById(autoBidder.getUserId());
                if (!(fullUser instanceof Bidder)) continue;
                Bidder fullBidder = (Bidder) fullUser;

                // Kiểm tra số dư
                if (fullBidder.getAccountBalance().compareTo(nextBid) < 0) continue;

                BigDecimal oldPrice = room.getCurrentPrice();

                synchronized (room) {
                    // Dùng placeAutoBid (không trigger processAutoBids in-room) để tránh đệ quy kép
                    room.placeAutoBid(fullBidder, nextBid);
                }

                // Lưu DB
                roomDAO.updateWithOptimisticLock(room, oldPrice);
                bidDAO.insert(new BidMessage(0, fullBidder.getUserId(), room.getId(), nextBid));

                // Broadcast giá mới cho tất cả client
                ClientHandler.broadcast(new com.google.gson.Gson().toJson(
                        new server.networks.dto.MessageDTO("UPDATE_PRICE",
                                room.getId() + ":" + nextBid.toPlainString() + ":" + fullBidder.getUsername())
                ));

                System.out.println(">>> [AutoBid] " + fullBidder.getUsername() +
                        " tự động bid " + nextBid + " vào phòng " + room.getId());

                // Đệ quy nếu còn auto bidder khác
                processAutoBids(room, fullBidder);
                break;
            }
        } catch (Exception e) {
            System.err.println(">>> [AutoBid Error] " + e.getMessage());
        }
    }

    public void autoUpdateStatuses() {
        LocalDateTime now = LocalDateTime.now();

        activeRooms.values().forEach(room -> {
            if (room.getStatus() != AuctionStatus.FINISHED
                    && room.getStatus() != AuctionStatus.PAID
                    && room.getStatus() != AuctionStatus.CANCELED) {

                if (room.getStatus() == AuctionStatus.OPEN && !now.isBefore(room.getStarttime())) {
                    room.setStatus(AuctionStatus.RUNNING);
                    updateRoomInDB(room);
                    System.out.println(">>> [Hệ thống] Room " + room.getId() + " START.");

                    ClientHandler.broadcast(new com.google.gson.Gson().toJson(
                            new server.networks.dto.MessageDTO("AUCTION_STARTED",
                                    String.valueOf(room.getId()))
                    ));
                } else if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                    // Phòng đang chạy & hết giờ -> kết thúc, xử lý thanh toán, broadcast và xóa khỏi RAM
                    room.setStatus(AuctionStatus.FINISHED);
                    processAuctionSettlement(room);
                    updateRoomInDB(room);

                    // Broadcast một lần duy nhất
                    ClientHandler.broadcast(new com.google.gson.Gson().toJson(
                            new server.networks.dto.MessageDTO("AUCTION_FINISHED",
                                    String.valueOf(room.getId()))
                    ));

                    // Xóa khỏi RAM sau 30 giây để giảm tải bộ nhớ
                    long roomIdToRemove = room.getId();
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(30_000);
                            activeRooms.remove(roomIdToRemove);
                            System.out.println(">>> [Manager] Đã xóa Room " + roomIdToRemove + " khỏi RAM.");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
            }
        });
    }
    private void processAuctionSettlement(AuctionRoom room) {
        User winner = room.getCurrentWinner();
        BigDecimal finalPrice = room.getCurrentPrice();

        if (winner == null || finalPrice == null) {
            room.setStatus(AuctionStatus.CANCELED);
            System.out.println(">>> [Hệ thống] Room " + room.getId() + " CANCELED (Không có người mua).");
            return;
        }

        try {
            User seller = userDAO.findById(room.getSellerID());

            if (winner.getAccountBalance().compareTo(finalPrice) >= 0) {
                winner.updateBalance(finalPrice.negate());
                seller.updateBalance(finalPrice);

                userDAO.update(winner);
                userDAO.update(seller);

                room.setStatus(AuctionStatus.PAID);
                System.out.println(">>> [Thanh toán] Room " + room.getId() +
                        " SUCCESS: " + winner.getUsername() + " đã trả " + finalPrice);
            } else {
                room.setStatus(AuctionStatus.CANCELED);
                System.out.println(">>> [Thanh toán] Room " + room.getId() +
                        " FAILED: Người thắng không đủ số dư.");
            }
        } catch (Exception e) {
            room.setStatus(AuctionStatus.CANCELED);
            System.err.println(">>> [Lỗi] Kết toán thất bại: " + e.getMessage());
        }
    }

    private void updateRoomInDB(AuctionRoom room) {
        BigDecimal currentPrice = room.getCurrentPrice();
        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.updateWithOptimisticLock(room, currentPrice);
            } catch (Exception e) {
                System.err.println(">>> [Lỗi DB] Update status thất bại: " + e.getMessage());
            }
        });
    }

    public List<AuctionRoom> getActiveRooms() {
        return new ArrayList<>(activeRooms.values());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Các method public được thêm để handlers gọi thay vì gọi DAO trực tiếp
    // ─────────────────────────────────────────────────────────────────────────

    /** Tìm phòng đấu giá theo id (dùng cho AuctionRequestHandler). */
    public AuctionRoom findRoomById(long roomId) {
        return activeRooms.get(roomId);
    }

    /**
     * Tạo phiên đấu giá mới và lưu vào DB.
     * Thay thế cho đoạn logic trước đây nằm trong ClientHandler.
     */
    public void createAuction(int sellerId, Item item,
                              LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        AuctionRoom room = new AuctionRoom(0, sellerId, item, startTime, endTime);
        room.setStatus(AuctionStatus.OPEN);
        room.setCurrentPrice(item.getStartingPrice());
        roomDAO.insert(room);
        reloadFromDatabase();
    }

    /**
     * Hủy phiên đấu giá do Seller yêu cầu.
     * Trả về "SUCCESS" hoặc thông báo lỗi.
     */
    public String cancelAuctionBySeller(int auctionId, int sellerId) throws Exception {
        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null)       return "Không tìm thấy phiên đấu giá!";
        if (room.getSellerID() != sellerId) return "Bạn không có quyền hủy phiên đấu giá này!";
        if (room.getStatus() == AuctionStatus.PAID
                || room.getStatus() == AuctionStatus.FINISHED)
            return "Phiên đã kết thúc/thanh toán, không thể hủy!";
        if (room.getStatus() == AuctionStatus.RUNNING && room.getCurrentWinner() != null)
            return "Phiên đang chạy đã có người đặt giá — không thể hủy!";

        room.setStatus(AuctionStatus.CANCELED);
        roomDAO.update(room);
        reloadFromDatabase();
        return "SUCCESS";
    }

    /**
     * Hủy phiên đấu giá do Admin yêu cầu.
     * Trả về "SUCCESS" hoặc thông báo lỗi.
     */
    public String cancelAuctionByAdmin(int auctionId) throws Exception {
        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null) return "Không tìm thấy phiên đấu giá!";
        if (room.getStatus() == AuctionStatus.PAID) return "Phiên đã thanh toán, không thể hủy!";

        room.setStatus(AuctionStatus.CANCELED);
        roomDAO.update(room);
        reloadFromDatabase();
        return "SUCCESS";
    }

    /**
     * Đăng ký AutoBid cho một phòng và kích hoạt ngay.
     * Thay thế cho đoạn logic trước đây nằm trong ClientHandler.
     */
    public void registerAutoBid(int auctionId, Bidder bidder,
                                BigDecimal maxBid, BigDecimal step) throws Exception {
        AuctionRoom room = new AuctionRoom();
        room.setId(auctionId);

        AutoBidConfig config = new AutoBidConfig();
        config.setAuctionId(room);
        config.setBidder(bidder);
        config.setMaxBid(maxBid);
        config.setIncrement(step);

        autoBidDAO.insert(config);
        triggerAutoBidsForRoom(auctionId, bidder);
    }

    /**
     * Hủy AutoBid của một phòng (xóa tất cả config theo auctionId).
     */
    public void cancelAutoBid(int auctionId) throws Exception {
        autoBidDAO.deleteByAuctionId(auctionId);
    }

    /**
     * Trả lịch sử bid của một phòng kèm username (dùng cho AuctionRequestHandler).
     */
    public List<java.util.Map<String, Object>> getBidHistory(int roomId) throws Exception {
        List<server.models.auction.BidMessage> bids =
                bidDAO.getBidHistoryByAuctionRoomId(roomId);
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (server.models.auction.BidMessage b : bids) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            String username = "Người dùng #" + b.getBidderId();
            try {
                User u = userDAO.findById(b.getBidderId());
                if (u != null) username = u.getUsername();
            } catch (Exception ignored) {}
            m.put("username", username);
            m.put("amount",   b.getBidAmount().doubleValue());
            m.put("time",     b.getTimestamp() != null ? b.getTimestamp().toString() : "");
            result.add(m);
        }
        return result;
    }

}