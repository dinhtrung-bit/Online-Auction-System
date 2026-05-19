package server.services;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.AutoBidDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.BidRecord;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.User;
import server.networks.AuctionBroadcastManager;
import server.networks.interfaces.BroadcastChannel;

/**
 * Facade chính cho nghiệp vụ đấu giá.
 * Public API giữ nguyên để các class khác không phải đổi cách gọi.
 */
public class AuctionService {

    private static AuctionService instance;

    private final AuctionRoomDAO roomDAO;
    private final ItemDAO itemDAO;
    private final BidMessageDAO bidDAO;
    private final UserDAO userDAO;
    private final AutoBidDAO autoBidDAO;
    private final BroadcastChannel broadcaster;

    private final ConcurrentHashMap<Long, AuctionRoom> activeRooms = new ConcurrentHashMap<>();

    private final AuctionNotificationService notificationService;
    private final AuctionSettlementService settlementService;
    private final AuctionAutoBidService autoBidService;
    private final AuctionBidService bidService;
    private final AuctionStatusService statusService;

    private AuctionService(
            AuctionRoomDAO roomDAO,
            ItemDAO itemDAO,
            BidMessageDAO bidDAO,
            UserDAO userDAO,
            AutoBidDAO autoBidDAO,
            BroadcastChannel broadcaster) {
        this.roomDAO = roomDAO;
        this.itemDAO = itemDAO;
        this.bidDAO = bidDAO;
        this.userDAO = userDAO;
        this.autoBidDAO = autoBidDAO;
        this.broadcaster = broadcaster;

        this.notificationService = (broadcaster instanceof AuctionBroadcastManager mgr)
                ? new AuctionNotificationService(mgr)
                : new AuctionNotificationService(new AuctionBroadcastManager(
                server.networks.ClientHandler.activeClients));
        this.settlementService = new AuctionSettlementService(roomDAO, userDAO);
        this.autoBidService = new AuctionAutoBidService(
                activeRooms, roomDAO, bidDAO, userDAO, autoBidDAO, notificationService);
        this.bidService = new AuctionBidService(
                activeRooms, roomDAO, bidDAO, userDAO, autoBidService, notificationService);
        this.statusService = new AuctionStatusService(
                activeRooms, roomDAO, settlementService, notificationService);

        loadRoomsFromDatabase();
    }

    public static synchronized AuctionService getInstance(
            AuctionRoomDAO roomDAO,
            ItemDAO itemDAO,
            BidMessageDAO bidDAO,
            UserDAO userDAO,
            AutoBidDAO autoBidDAO,
            BroadcastChannel broadcaster) {
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

    public BroadcastChannel getBroadcaster() {
        return broadcaster;
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

    public void createAuction(int sellerId, Item item, LocalDateTime startTime, LocalDateTime endTime)
            throws Exception {
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

    public String cancelAuctionBySeller(int auctionId, int sellerId) throws Exception {
        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null) {
            return "Không tìm thấy phiên đấu giá.";
        }

        synchronized (room) {
            if (room.getSellerID() != sellerId) {
                return "Bạn không có quyền hủy phiên đấu giá này.";
            }
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
        if (room == null) {
            return "Không tìm thấy phiên đấu giá.";
        }

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

    public String handleBidRequest(Long roomId, Bidder bidder, double amount) {
        return bidService.handleBidRequest(roomId, bidder, amount);
    }

    public void registerAutoBid(int auctionId, Bidder bidder, BigDecimal maxBid, BigDecimal step)
            throws Exception {
        autoBidService.registerAutoBid(auctionId, bidder, maxBid, step);
    }

    public void cancelAutoBid(int auctionId, int bidderId) throws Exception {
        autoBidService.cancelAutoBid(auctionId, bidderId);
    }

    public void triggerAutoBidsForRoom(long roomId, Bidder trigger) {
        autoBidService.triggerAutoBidsForRoom(roomId, trigger);
    }

    public void autoUpdateStatuses() {
        statusService.autoUpdateStatuses();
    }

    public List<Map<String, Object>> getBidHistory(int roomId) throws Exception {
        List<BidRecord> bids = bidDAO.getBidHistoryByAuctionRoomId(roomId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (BidRecord b : bids) {
            String username = "Người dùng #" + b.getBidderId();
            try {
                User u = userDAO.findById(b.getBidderId());
                if (u != null) {
                    username = u.getUsername();
                }
            } catch (Exception ignored) {
                // giữ username mặc định
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", username);
            m.put("amount",   b.getBidAmount().doubleValue());
            m.put("time",     b.getTimestamp() != null ? b.getTimestamp().toString() : "");
            result.add(m);
        }
        return result;
    }
}