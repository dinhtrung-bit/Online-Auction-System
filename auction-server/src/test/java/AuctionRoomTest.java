import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Import các class từ project của bạn
import server.models.auction.AuctionRoom;
import server.models.items.Art;
import server.models.items.Item;
import server.models.users.Bidder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionRoomTest {

    @Test
    public void testAntiSnipingExtension() throws Exception {
        // Thêm 'throws Exception' vì hàm placeBid của bạn có ném lỗi

        // 1. Tạo dữ liệu giả (Mock data) cho Item và Bidder để test
        Item item = new Art(1, "Bức tranh Test", new BigDecimal("100000"), "Mô tả test");
        Bidder testBidder = new Bidder(1, "bidder_test", "123", "test@email.com", new BigDecimal("5000000"));

        // 2. Giả lập một phòng đấu giá sắp kết thúc (còn 10 giây)
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime nearEnd = LocalDateTime.now().plusSeconds(10);

        // Dùng đúng Constructor 6 tham số của bạn: (id, item, currentWinner, startTime, endTime, currentPrice)
        AuctionRoom room = new AuctionRoom(1, item, startTime, nearEnd);

        // 3. Thực hiện đặt giá (Place Bid)
        room.placeBid(testBidder, new BigDecimal("200000"));

        // 4. Kiểm tra xem thời gian kết thúc có được gia hạn thêm 60 giây không
        assertTrue(room.getEndTime().isAfter(nearEnd));

        // In ra màn hình để xem cho rõ
        System.out.println("Thời gian dự kiến kết thúc: " + nearEnd);
        System.out.println("Thời gian sau khi bị Sniping: " + room.getEndTime());
    }
}