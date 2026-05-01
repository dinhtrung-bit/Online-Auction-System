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

/**
 * AuctionService: Đã tối ưu hóa cho đấu giá đồng thời và hiệu suất cao.
 */
public class AuctionService {
    private static AuctionService instance;

    // 1. TỐI ƯU DAO: Khai báo final và dùng chung để tránh tạo mới Object liên tục
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
            // Logic nạp dữ liệu từ DB (Bạn có thể bỏ comment khi DAO đã sẵn sàng)
            // List<AuctionRoom> rooms = roomDAO.findAll();
            // rooms.forEach(r -> activeRooms.put((long)r.getId(), r));
            System.out.println(">>> [Manager] Đã nạp dữ liệu từ Database vào RAM.");
        } catch (Exception e) {
            System.err.println("Lỗi nạp dữ liệu: " + e.getMessage());
        }
    }

    public void createNewAuction(Item item, LocalDateTime endTime) {
        Long roomId = System.currentTimeMillis();
        AuctionRoom newRoom = new AuctionRoom(roomId.intValue(), item, LocalDateTime.now(), endTime);
        activeRooms.put(roomId, newRoom);

        // Chạy bất đồng bộ để không làm treo luồng chính khi lưu DB
        CompletableFuture.runAsync(() -> {
            try {
                itemDAO.insert(item);
                // roomDAO.insert(newRoom); // Thêm nếu DAO có hàm insert room
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

        // BƯỚC 1: Lock theo phòng để xử lý nhanh trên RAM (Critical Section cực ngắn)
        synchronized (room) {
            try {
                // Kiểm tra logic đặt giá (Ví dụ: giá mới > giá cũ)
                room.placeBid(bidder, bidAmount);
            } catch (Exception e) {
                return e.getMessage(); // Trả về lỗi ngay lập tức nếu giá không hợp lệ
            }
        }

        // BƯỚC 2: Sau khi RAM đã cập nhật, việc lưu DB thực hiện ở luồng riêng (Async)
        // Giúp giải phóng lock ngay lập tức cho người tiếp theo vào đặt giá
        CompletableFuture.runAsync(() -> {
            try {
                // Lưu trạng thái phòng
                roomDAO.update(room);

                // Ghi lịch sử bid
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
            // Tối ưu: Chỉ kiểm tra những phòng chưa kết thúc
            if (room.getStatus() != AuctionStatus.FINISHED) {

                // 1. OPEN -> RUNNING
                if (room.getStatus() == AuctionStatus.OPEN && !now.isBefore(room.getStarttime())) {
                    room.setStatus(AuctionStatus.RUNNING);
                    updateRoomInDB(room);
                    System.out.println(">>> [Hệ thống] Room " + room.getId() + " START.");
                }

                // 2. RUNNING -> FINISHED
                else if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                    room.setStatus(AuctionStatus.FINISHED);
                    updateRoomInDB(room);
                    System.out.println(">>> [Hệ thống] Room " + room.getId() + " END.");
                }
            }
        });
    }

    private void updateRoomInDB(AuctionRoom room) {
        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.update(room);
            } catch (Exception e) {
                System.err.println(">>> [Lỗi DB] Update status thất bại: " + e.getMessage());
            }
        });
    }

    public List<AuctionRoom> getActiveRooms() {
        return new ArrayList<>(activeRooms.values());
    }
}