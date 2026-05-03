package server.services;

import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.ItemDAOimpl;
import server.dao.impl.BidMessageDAOImpl;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.BidMessageDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.BidMessage;
import server.models.items.Item;
import server.models.users.Bidder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class AuctionService {
    private static AuctionService instance;

    private final AuctionRoomDAO roomDAO = new AuctionRoomDAOImpl();
    private final ItemDAO itemDAO = new ItemDAOimpl();
    private final BidMessageDAO bidDAO = new BidMessageDAOImpl();

    private ConcurrentHashMap<Long, AuctionRoom> activeRooms;

    private AuctionService() {
        activeRooms = new ConcurrentHashMap<>();
        loadRoomsFromDatabase();
    }

    public static synchronized AuctionService getInstance() {
        if (instance == null) {
            instance = new AuctionService();
        }
        return instance;
    }

    private void loadRoomsFromDatabase() {
        try {
            List<AuctionRoom> rooms = roomDAO.findAll();
            rooms.forEach(r -> activeRooms.put((long)r.getId(), r));
            System.out.println(">>> [Manager] Đã nạp dữ liệu từ Database vào RAM.");
        } catch (Exception e) {
            System.err.println("Lỗi nạp dữ liệu: " + e.getMessage());
        }
    }

    public void createNewAuction(Item item, LocalDateTime endTime) {
        Long roomId = System.currentTimeMillis();
        AuctionRoom newRoom = new AuctionRoom(roomId.intValue(), item, LocalDateTime.now(), endTime);
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

    // ================= TỐI ƯU ĐẶT GIÁ ĐỒNG THỜI =================
    public String handleBidRequest(Long roomId, Bidder bidder, double amount) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return "LỖI: Không tìm thấy phòng đấu giá!";

        BigDecimal bidAmount = BigDecimal.valueOf(amount);
        BigDecimal oldPrice; // Khai báo biến giữ giá cũ

        // BƯỚC 1: Lock theo phòng và CHỤP GIÁ CŨ
        synchronized (room) {
            try {
                oldPrice = room.getCurrentPrice(); // Chụp lại giá cũ trước khi thay đổi
                room.placeBid(bidder, bidAmount);
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        // BƯỚC 2: Truyền giá cũ xuống DAO để Database kiểm tra Optimistic Lock
        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.update(room, oldPrice); // Truyền oldPrice vào đây

                BidMessage bid = new BidMessage(0, bidder.getUserId(), roomId.intValue(), bidAmount);
                bidDAO.insert(bid);

                System.out.println(">>> [DB Success] Phòng " + roomId + ": " + bidder.getUsername() + " bid " + amount);
            } catch (Exception e) {
                System.err.println(">>> [DB Error] Lỗi lưu lịch sử đặt giá: " + e.getMessage());
            }
        });

        return "SUCCESS";
    }

    // ================= TỐI ƯU QUÉT TRẠNG THÁI =================
    public void autoUpdateStatuses() {
        LocalDateTime now = LocalDateTime.now();

        activeRooms.values().forEach(room -> {
            if (room.getStatus() != AuctionStatus.FINISHED) {
                if (room.getStatus() == AuctionStatus.OPEN && !now.isBefore(room.getStarttime())) {
                    room.setStatus(AuctionStatus.RUNNING);
                    updateRoomInDB(room);
                    System.out.println(">>> [Hệ thống] Room " + room.getId() + " START.");
                }
                else if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                    room.setStatus(AuctionStatus.FINISHED);
                    updateRoomInDB(room);
                    System.out.println(">>> [Hệ thống] Room " + room.getId() + " END.");
                }
            }
        });
    }

    private void updateRoomInDB(AuctionRoom room) {
        // Với hàm update Status tự động, giá tiền không thay đổi nên oldPrice bằng currentPrice
        BigDecimal currentPrice = room.getCurrentPrice();
        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.update(room, currentPrice);
            } catch (Exception e) {
                System.err.println(">>> [Lỗi DB] Update status thất bại: " + e.getMessage());
            }
        });
    }

    public List<AuctionRoom> getActiveRooms() {
        return new ArrayList<>(activeRooms.values());
    }
}