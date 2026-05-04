package test.java;

import org.junit.jupiter.api.Test;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Art;
import server.models.items.Item;
import server.services.AuctionService;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionServiceTest {

    @Test
    public void testAutoUpdateStatuses() throws Exception {
        AuctionService service = AuctionService.getInstance();

        // Dùng Reflection để nhét dữ liệu giả vào activeRooms (bypass Database load)
        Field activeRoomsField = AuctionService.class.getDeclaredField("activeRooms");
        activeRoomsField.setAccessible(true);
        ConcurrentHashMap<Long, AuctionRoom> mockRooms = new ConcurrentHashMap<>();

        Item item1 = new Art(1, "Item 1", new BigDecimal("100"), "Desc");
        Item item2 = new Art(2, "Item 2", new BigDecimal("100"), "Desc");

        // Phòng 1: Quá hạn kết thúc
        // CẬP NHẬT: Thêm tham số sellerID = 99
        AuctionRoom roomEnded = new AuctionRoom(1, 99, item1, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusMinutes(1));
        roomEnded.setStatus(AuctionStatus.RUNNING); // Ép trạng thái giả

        // Phòng 2: Đến giờ mở bán
        // CẬP NHẬT: Thêm tham số sellerID = 99
        AuctionRoom roomStarting = new AuctionRoom(2, 99, item2, LocalDateTime.now().minusSeconds(5), LocalDateTime.now().plusHours(1));
        roomStarting.setStatus(AuctionStatus.OPEN); // Ép trạng thái mở ban đầu

        mockRooms.put(1L, roomEnded);
        mockRooms.put(2L, roomStarting);
        activeRoomsField.set(service, mockRooms);

        // Chạy hàm quét tự động
        service.autoUpdateStatuses();

        // CẬP NHẬT KIỂM TRA KẾT QUẢ:
        // Phòng 1 hết giờ nhưng KHÔNG có ai bid (winner = null) -> Hệ thống tự động chuyển sang CANCELED thay vì FINISHED
        assertEquals(AuctionStatus.CANCELED, roomEnded.getStatus());

        // Phòng 2 đến giờ mở bán -> Chuyển sang RUNNING
        assertEquals(AuctionStatus.RUNNING, roomStarting.getStatus());
    }
}