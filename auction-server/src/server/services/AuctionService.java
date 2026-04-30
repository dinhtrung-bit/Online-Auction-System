package server.services;

import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.ItemDAOimpl;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.ItemDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Item;
import server.models.users.Bidder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuctionManager: "Bộ não" trung tâm điều phối toàn bộ hệ thống.
 * Kết nối trực tiếp giữa Socket (Mạng) và DAO (Database).
 */
public class AuctionService {
    private static AuctionService instance;

    // Quản lý các phòng đang hoạt động trong RAM để xử lý thời gian thực nhanh hơn
    private ConcurrentHashMap<Long, AuctionRoom> activeRooms;

    private AuctionService() {
        activeRooms = new ConcurrentHashMap<>();
        // Khi Server bật lên, ta nên load các phòng từ DB vào RAM
        loadRoomsFromDatabase();
    }

    public static synchronized AuctionService getInstance() {
        if (instance == null) {
            instance = new AuctionService();
        }
        return instance;
    }

    // ================= QUẢN LÝ DỮ LIỆU & PERSISTENCE =================

    private void loadRoomsFromDatabase() {
        try {
            // Sau này bạn cài đặt AuctionRoomDAOImpl, hãy gọi findAll() ở đây
            // List<AuctionRoom> rooms = new AuctionRoomDAOImpl().findAll();
            // rooms.forEach(r -> activeRooms.put(r.getId(), r));
            System.out.println(">>> [Manager] Đã nạp dữ liệu từ Database vào RAM.");
        } catch (Exception e) {
            System.err.println("Lỗi nạp dữ liệu: " + e.getMessage());
        }
    }

    // ================= NGHIỆP VỤ CHO SELLER (ĐĂNG TIN) =================

    /**
     * Khi Seller đăng sản phẩm, ta tạo Item và AuctionRoom tương ứng.
     */
    public void createNewAuction(Item item, LocalDateTime endTime) {
        // 1. Tạo ID ảo hoặc lấy từ Database (Ở đây giả định lấy currentTime làm ID)
        Long roomId = System.currentTimeMillis();

        // ĐÃ SỬA LỖI Ở ĐÂY: Ép kiểu roomId.intValue() và thêm LocalDateTime.now() cho starttime
        AuctionRoom newRoom = new AuctionRoom(roomId.intValue(), item, LocalDateTime.now(), endTime);

        // 2. Lưu vào RAM để quản lý
        activeRooms.put(roomId, newRoom);

        // 3. Lưu xuống Database (Phần của P2)
        try {
            // Lưu Item trước
            ItemDAO itemDAO = new ItemDAOimpl();
            itemDAO.insert(item);

            // Sau đó lưu Room (AuctionRoomDAO)
            System.out.println(">>> [Manager] Đã tạo phiên đấu giá mới cho: " + item.getName());
        } catch (Exception e) {
            System.err.println("Lỗi lưu DB: " + e.getMessage());
        }
    }

    // ================= NGHIỆP VỤ CHO BIDDER (ĐẶT GIÁ) =================

    /**
     * Xử lý cú click "Đặt giá" từ Client gửi lên.
     */
    public String handleBidRequest(Long roomId, Bidder bidder, double amount) {
        AuctionRoom room = activeRooms.get(roomId);

        if (room == null) return "LỖI: Không tìm thấy phòng đấu giá!";

        try {
            // Gọi logic lõi trong Model (đã có Anti-sniping và kiểm tra số dư)
            room.placeBid(bidder, BigDecimal.valueOf(amount));

            // Ghi lại lịch sử đặt giá vào Database
            saveBidToDatabase(roomId, bidder, amount);

            return "SUCCESS";
        } catch (Exception e) {
            return e.getMessage(); // Trả về lỗi: "Giá quá thấp", "Hết thời gian", v.v.
        }
    }

    private void saveBidToDatabase(Long roomId, Bidder bidder, double amount) {
        try {
            // Gọi BidMessageDAO để insert record mới
            // BidMessageDAO bidDAO = new BidMessageDAOImpl();
            // bidDAO.insert(new BidMessage(Long.parseLong(bidder.getUserId()), roomId, amount));
        } catch (Exception e) {
            System.err.println("Lỗi ghi lịch sử Bid: " + e.getMessage());
        }
    }

    // ================= QUẢN LÝ TRẠNG THÁI TỰ ĐỘNG =================

    /**
     * VÁ LỖI 3.1.4: Hàm này được Server gọi mỗi giây để quét toàn bộ trạng thái phòng
     */
    public void autoUpdateStatuses() {
        LocalDateTime now = LocalDateTime.now();
        activeRooms.values().forEach(room -> {

            // 1. Chuyển trạng thái từ OPEN -> RUNNING (Đến giờ mở cửa)
            if (room.getStatus() == AuctionStatus.OPEN && !now.isBefore(room.getStarttime())) {
                room.setStatus(AuctionStatus.RUNNING);
                System.out.println(">>> [Hệ thống] Phiên đấu giá ID: " + room.getId() + " đã bắt đầu (RUNNING).");
                updateRoomInDB(room);
            }

            // 2. Chuyển trạng thái từ RUNNING -> FINISHED (Hết giờ đấu giá)
            else if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                room.setStatus(AuctionStatus.FINISHED);
                System.out.println(">>> [Hệ thống] Đóng phiên đấu giá ID: " + room.getId() + " (FINISHED).");

                // Log người chiến thắng
                if (room.getCurrentWinner() != null) {
                    System.out.println("    -> Người chiến thắng: " + room.getCurrentWinner().getUsername() + " với giá " + room.getCurrentPrice());
                    // Lưu ý: Logic chuyển từ FINISHED -> PAID sẽ nằm ở một hàm thanh toán riêng biệt sau này
                } else {
                    System.out.println("    -> Không có ai tham gia đặt giá.");
                }
                updateRoomInDB(room);
            }
        });
    }

    // Hàm phụ trợ giúp code gọn gàng, tránh lặp lại try-catch
    private void updateRoomInDB(AuctionRoom room) {
        try {
            AuctionRoomDAO roomDAO = new AuctionRoomDAOImpl();
            roomDAO.update(room);
        } catch (Exception e) {
            System.err.println(">>> [Lỗi Database] Không thể cập nhật phòng ID " + room.getId() + ": " + e.getMessage());
        }
    }

    // Lấy danh sách cho Client hiển thị lên JavaFX
    public List<AuctionRoom> getActiveRooms() {
        return new ArrayList<>(activeRooms.values());
    }
}