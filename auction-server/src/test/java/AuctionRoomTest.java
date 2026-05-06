package test.java;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.exceptions.InvalidBidException;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Art;
import server.models.items.Item;
import server.models.users.Bidder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionRoomTest {

    private Item item;
    private AuctionRoom room;
    private Bidder bidderA;
    private Bidder bidderB;
    private Bidder bidderC;

    @BeforeEach
    public void setUp() {
        // Khởi tạo Item giá 1000
        item = new Art(1, "Mona Lisa Copy", new BigDecimal("1000"), "Bức tranh test");

        // Khởi tạo 3 Bidders với số dư dồi dào (50.000)
        bidderA = new Bidder(1, "Alice", "pw", "alice@mail.com", new BigDecimal("50000"));
        bidderB = new Bidder(2, "Bob", "pw", "bob@mail.com", new BigDecimal("50000"));
        bidderC = new Bidder(3, "Charlie", "pw", "charlie@mail.com", new BigDecimal("50000"));

        // Mặc định tạo phòng đang chạy (RUNNING), kết thúc sau 1 giờ
        room = new AuctionRoom(1, 99, item, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(1));
    }

    // =========================================================================================
    // NHÓM 1: KIỂM THỬ RÀNG BUỘC THỜI GIAN & TRẠNG THÁI (STATUS & TIME CONSTRAINTS)
    // =========================================================================================

    @Test
    public void testBidWhenRoomNotRunning_ShouldThrowException() {
        // Tạo phòng ở tương lai (Trạng thái OPEN)
        AuctionRoom futureRoom = new AuctionRoom(2, 99, item, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));

        Exception ex = assertThrows(InvalidBidException.class, () -> futureRoom.placeBid(bidderA, new BigDecimal("1500")));
        assertTrue(ex.getMessage().contains("đã kết thúc")); // Dù là OPEN, nhưng hàm placeBid chặn != RUNNING
    }

    @Test
    public void testBidWhenRoomExpired_ShouldThrowExceptionAndChangeStatus() {
        // Tạo phòng đã quá hạn (thời gian kết thúc ở quá khứ)
        AuctionRoom expiredRoom = new AuctionRoom(3, 99, item, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusMinutes(1));
        expiredRoom.setStatus(AuctionStatus.RUNNING); // Cố tình ép trạng thái để lừa hệ thống

        Exception ex = assertThrows(InvalidBidException.class, () -> expiredRoom.placeBid(bidderA, new BigDecimal("1500")));
        assertTrue(ex.getMessage().contains("đã kết thúc"));
        assertEquals(AuctionStatus.FINISHED, expiredRoom.getStatus(), "Phòng phải tự chuyển sang FINISHED khi phát hiện quá hạn");
    }

    // =========================================================================================
    // NHÓM 2: KIỂM THỬ ĐẶT GIÁ THỦ CÔNG (MANUAL BIDDING)
    // =========================================================================================

    @Test
    public void testValidManualBid() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("1500"));

        assertEquals(new BigDecimal("1500"), room.getCurrentPrice());
        assertEquals(bidderA.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test
    public void testInvalidBid_LowerThanStartPrice() {
        // Chưa có ai bid, giá sàn là 1000. Đặt 900 phải bị chặn.
        Exception ex = assertThrows(InvalidBidException.class, () -> room.placeBid(bidderA, new BigDecimal("900")));
        assertTrue(ex.getMessage().contains("lớn hơn 1000"));
    }

    @Test
    public void testInvalidBid_LowerThanCurrentPrice() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("2000"));

        // Bob cố tình đặt 1500 khi giá đang là 2000
        Exception ex = assertThrows(InvalidBidException.class, () -> room.placeBid(bidderB, new BigDecimal("1500")));
        assertTrue(ex.getMessage().contains("lớn hơn 2000"));
    }

    @Test
    public void testInvalidBid_InsufficientBalance() {
        // Tạo người dùng chỉ có 500đ
        Bidder poorBidder = new Bidder(4, "Poor", "pw", "poor@mail.com", new BigDecimal("500"));

        // Cố tình đặt 1500đ
        Exception ex = assertThrows(InvalidBidException.class, () -> room.placeBid(poorBidder, new BigDecimal("1500")));
        assertTrue(ex.getMessage().contains("Tài khoản không đủ số dư"));
    }

    // =========================================================================================
    // NHÓM 3: KIỂM THỬ CƠ CHẾ CHỐNG BẮN TỈA GIÁ (ANTI-SNIPING)
    // =========================================================================================

    @Test
    public void testAntiSniping_NotTriggered_WhenMoreThan30SecondsLeft() throws InvalidBidException {
        LocalDateTime originalEndTime = room.getEndTime();
        room.placeBid(bidderA, new BigDecimal("1500")); // Phòng còn 1 giờ

        assertEquals(originalEndTime, room.getEndTime(), "Không được gia hạn vì thời gian còn nhiều hơn 30s");
    }

    @Test
    public void testAntiSniping_Triggered_WhenLessThan30SecondsLeft() throws InvalidBidException {
        // Tạo phòng chỉ còn 15 giây
        LocalDateTime closeEndTime = LocalDateTime.now().plusSeconds(15);
        AuctionRoom snipingRoom = new AuctionRoom(4, 99, item, LocalDateTime.now().minusMinutes(10), closeEndTime);

        snipingRoom.placeBid(bidderA, new BigDecimal("1500"));

        // Mong đợi thời gian cộng thêm đúng 60 giây
        LocalDateTime expectedNewEndTime = closeEndTime.plusSeconds(60);
        assertTrue(snipingRoom.getEndTime().isEqual(expectedNewEndTime) || snipingRoom.getEndTime().isAfter(closeEndTime));
    }

    @Test
    public void testAntiSniping_MaxExtensionsReached() throws InvalidBidException {
        // Tạo phòng chỉ còn 10 giây
        AuctionRoom snipingRoom = new AuctionRoom(5, 99, item, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusSeconds(10));

        // Bắn tỉa 6 lần liên tiếp
        for (int i = 1; i <= 6; i++) {
            snipingRoom.placeBid(bidderA, new BigDecimal(1000 + i * 100));
        }

        // Tối đa 5 lần gia hạn, mỗi lần 60s -> Tổng gia hạn là 300s
        LocalDateTime maxAllowedTime = LocalDateTime.now().plusSeconds(10).plusSeconds(300);

        // Đảm bảo thời gian kết thúc không được vượt quá thời gian tối đa cho phép + sai số 1 giây
        assertTrue(snipingRoom.getEndTime().isBefore(maxAllowedTime.plusSeconds(2)));
    }

    // =========================================================================================
    // NHÓM 4: KIỂM THỬ ĐĂNG KÝ AUTO-BID (AUTO-BID REGISTRATION)
    // =========================================================================================

    @Test
    public void testRegisterAutoBid_InsufficientBalance() {
        // Số dư Bob là 50,000, nhưng thiết lập Max Bid 100,000
        Exception ex = assertThrows(InvalidBidException.class, () -> room.registerAutoBid(bidderB, new BigDecimal("100000"), new BigDecimal("500")));
        assertTrue(ex.getMessage().contains("Số dư không đủ để bảo lãnh"));
    }

    // =========================================================================================
    // NHÓM 5: KIỂM THỬ LOGIC TRANH GIÁ TỰ ĐỘNG (AUTO-BIDDING WAR LOGIC)
    // =========================================================================================
    @Test
    public void testAutoBid_NormalStep() throws InvalidBidException {
        // Bob cài Auto-bid: Max 5000, bước giá 500
        room.registerAutoBid(bidderB, new BigDecimal("5000"), new BigDecimal("500"));

        // Alice đặt thủ công 1500
        room.placeBid(bidderA, new BigDecimal("1500"));

        // Hệ thống phát hiện Bob có Auto-bid, tự động đè giá: 1500 + 500 = 2000
        assertEquals(new BigDecimal("2000"), room.getCurrentPrice());
        assertEquals(bidderB.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test
    public void testAutoBid_AllIn() throws InvalidBidException {
        // Bob cài Auto-bid: Max 2200, bước giá 500
        room.registerAutoBid(bidderB, new BigDecimal("2200"), new BigDecimal("500"));

        // Alice đặt thủ công 2000
        room.placeBid(bidderA, new BigDecimal("2000"));

        // Bước giá đáng lẽ là 2000 + 500 = 2500 (Vượt Max 2200). Hệ thống phải ép All-In 2200.
        assertEquals(new BigDecimal("2200"), room.getCurrentPrice());
        assertEquals(bidderB.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test
    public void testAutoBid_Versus_AutoBid() throws InvalidBidException {
        // Cả 2 cùng cài Auto-Bid
        room.registerAutoBid(bidderB, new BigDecimal("3000"), new BigDecimal("200"));
        room.registerAutoBid(bidderC, new BigDecimal("4000"), new BigDecimal("200"));

        // KHÔNG CẦN ALICE MỒI GIÁ NỮA.
        // Ngay khi Charlie đăng ký xong, hệ thống đã tự động cho 2 bot giằng co nhau.
        // Bob chạm trần 3000 và gục ngã. Charlie thắng.

        assertEquals(bidderC.getUserId(), room.getCurrentWinner().getUserId());
        assertTrue(room.getCurrentPrice().compareTo(new BigDecimal("3000")) >= 0);
    }

    @Test
    public void testAutoBid_TieBreaker() throws InvalidBidException, InterruptedException {
        // Bob đăng ký Max 3000 TRƯỚC
        room.registerAutoBid(bidderB, new BigDecimal("3000"), new BigDecimal("200"));

        Thread.sleep(100); // Đảm bảo Bob có registerTime sớm hơn

        // Charlie đăng ký Max 3000 SAU
        room.registerAutoBid(bidderC, new BigDecimal("3000"), new BigDecimal("200"));

        // KHÔNG CẦN ALICE ĐẶT GIÁ 2900 NỮA.
        // Charlie vừa vào phòng là 2 bên tự động đẩy giá lên 3000.

        // LUẬT TIE-BREAKER: Người đăng ký trước (Bob) chiến thắng ở mốc chạm trán (3000)
        assertEquals(new BigDecimal("3000"), room.getCurrentPrice());
        assertEquals(bidderB.getUserId(), room.getCurrentWinner().getUserId());
    }
}