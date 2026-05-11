import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * AuctionRoomTest — thiết kế theo giáo trình UET.CS2043
 *
 * Kỹ thuật:
 *   [EP]    = Equivalence Partitioning  (slide 10-16)
 *   [BVA]   = Boundary Value Analysis   (slide 11, 17-18)
 *   [2-way] = Pairwise combinatorial    (slide 22-24)
 *   [EG]    = Error Guessing            (slide 9)
 *   [@BeforeEach] lifecycle             (slide 38)
 */
public class AuctionRoomTest {

    private Item item;
    private AuctionRoom room;
    private Bidder bidderA;
    private Bidder bidderB;
    private Bidder bidderC;

    // @BeforeEach: khởi tạo trạng thái sạch trước mỗi test (slide 38)
    @BeforeEach
    void setUp() {
        item    = new Art(1, "Mona Lisa Copy", new BigDecimal("1000"), "Bức tranh test");
        bidderA = new Bidder(1, "Alice",   "pw", "alice@mail.com",   new BigDecimal("50000"));
        bidderB = new Bidder(2, "Bob",     "pw", "bob@mail.com",     new BigDecimal("50000"));
        bidderC = new Bidder(3, "Charlie", "pw", "charlie@mail.com", new BigDecimal("50000"));
        // Phòng RUNNING, kết thúc sau 1 giờ
        room = new AuctionRoom(1, 99, item,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1));
    }

    // ================================================================
    // NHÓM 1: Trạng thái phòng — EP theo trạng thái (OPEN / RUNNING / EXPIRED)
    // ================================================================

    @Test @DisplayName("[EP] Đặt giá khi phòng OPEN (chưa bắt đầu) — phải bị từ chối")
    void testBid_WhenOpen_EP() {
        AuctionRoom futureRoom = new AuctionRoom(2, 99, item,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2));
        assertThrows(InvalidBidException.class,
                () -> futureRoom.placeBid(bidderA, new BigDecimal("1500")));
    }

    @Test @DisplayName("[EP] Đặt giá khi phòng RUNNING — hợp lệ")
    void testBid_WhenRunning_EP() {
        assertDoesNotThrow(() -> room.placeBid(bidderA, new BigDecimal("1500")));
    }

    @Test @DisplayName("[EP] Đặt giá khi phòng đã hết giờ (EXPIRED) — phải bị từ chối và status = FINISHED")
    void testBid_WhenExpired_EP() {
        AuctionRoom expiredRoom = new AuctionRoom(3, 99, item,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusMinutes(1));
        expiredRoom.setStatus(AuctionStatus.RUNNING);

        assertThrows(InvalidBidException.class,
                () -> expiredRoom.placeBid(bidderA, new BigDecimal("1500")));
        assertEquals(AuctionStatus.FINISHED, expiredRoom.getStatus());
    }

    // ================================================================
    // NHÓM 2: Giá đặt — EP + BVA xung quanh giá hiện tại
    // Ngưỡng: currentPrice = 1000 (giá sàn ban đầu)
    // BVA: 999 (min-), 1000 (min = đúng bằng), 1001 (min+ = hợp lệ nhỏ nhất)
    // ================================================================

    @Test @DisplayName("[EP] Giá đặt hợp lệ — lớn hơn giá hiện tại rõ ràng")
    void testBid_ValidPrice_EP() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("2000"));
        assertEquals(new BigDecimal("2000"), room.getCurrentPrice());
        assertEquals(bidderA.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test @DisplayName("[BVA] Giá đặt = giá hiện tại (1000) — boundary, KHÔNG hợp lệ")
    void testBid_EqualCurrentPrice_BVA() {
        // 1000 == 1000 → compareTo <= 0 → phải throw
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(bidderA, new BigDecimal("1000")));
    }

    @Test @DisplayName("[BVA] Giá đặt = giá hiện tại - 1 (999) — boundary min-, KHÔNG hợp lệ")
    void testBid_OneBelowCurrentPrice_BVA() {
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(bidderA, new BigDecimal("999")));
    }

    @Test @DisplayName("[BVA] Giá đặt = giá hiện tại + 1 (1001) — boundary min+, hợp lệ nhỏ nhất")
    void testBid_OneAboveCurrentPrice_BVA() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("1001"));
        assertEquals(new BigDecimal("1001"), room.getCurrentPrice());
    }

    @Test @DisplayName("[BVA] Giá đặt thấp hơn giá người bid trước — không hợp lệ")
    void testBid_LowerThanLeadingBid_BVA() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("2000"));
        // Bob cố đặt 1999 < 2000
        Exception ex = assertThrows(InvalidBidException.class,
                () -> room.placeBid(bidderB, new BigDecimal("1999")));
        assertTrue(ex.getMessage().contains("lớn hơn 2000"));
    }

    // ================================================================
    // NHÓM 3: Số dư người đặt giá — EP + BVA
    // EP: balance > amount / balance == amount / balance < amount
    // BVA: ngưỡng đúng bằng, thiếu 0.01
    // ================================================================

    @Test @DisplayName("[EP] Số dư không đủ — bị từ chối")
    void testBid_InsufficientBalance_EP() {
        Bidder poorBidder = new Bidder(4, "Poor", "pw", "p@mail.com", new BigDecimal("500"));
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(poorBidder, new BigDecimal("1500")));
    }

    @Test @DisplayName("[BVA] Số dư đúng bằng giá đặt — hợp lệ (boundary)")
    void testBid_BalanceExactlyEnough_BVA() {
        // Bidder có đúng 1500, đặt 1500 → hợp lệ
        Bidder exactBidder = new Bidder(5, "Exact", "pw", "e@mail.com", new BigDecimal("1500"));
        assertDoesNotThrow(() -> room.placeBid(exactBidder, new BigDecimal("1500")));
    }

    @Test @DisplayName("[BVA] Số dư ít hơn giá đặt 0.01 — không hợp lệ (boundary min-)")
    void testBid_BalanceOnePennyShort_BVA() {
        Bidder almostBidder = new Bidder(6, "Almost", "pw", "al@mail.com", new BigDecimal("1499.99"));
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(almostBidder, new BigDecimal("1500")));
    }

    // ================================================================
    // NHÓM 4: Lịch sử đặt giá — kiểm tra state sau bid
    // ================================================================

    @Test @DisplayName("[EP] Sau 1 bid hợp lệ — winner và giá được cập nhật đúng")
    void testBid_StateAfterOneBid_EP() throws InvalidBidException {
        assertNull(room.getCurrentWinner()); // chưa có ai bid
        room.placeBid(bidderA, new BigDecimal("1500"));
        assertEquals(new BigDecimal("1500"), room.getCurrentPrice());
        assertNotNull(room.getCurrentWinner());
        assertEquals(bidderA.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test @DisplayName("[EP] Sau nhiều bid — winner là người đặt cao nhất cuối cùng")
    void testBid_MultipleSequential_EP() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("1500"));
        room.placeBid(bidderB, new BigDecimal("2000"));
        room.placeBid(bidderC, new BigDecimal("3000"));

        assertEquals(new BigDecimal("3000"), room.getCurrentPrice());
        assertEquals(bidderC.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test @DisplayName("[EG] Cùng người đặt giá hai lần liên tiếp — hợp lệ nếu giá sau > giá trước")
    void testBid_SamePersonBidTwice_EG() throws InvalidBidException {
        // [EG] Không có quy định cấm cùng người đặt giá 2 lần
        room.placeBid(bidderA, new BigDecimal("1500"));
        room.placeBid(bidderA, new BigDecimal("2000"));
        assertEquals(new BigDecimal("2000"), room.getCurrentPrice());
        assertEquals(bidderA.getUserId(), room.getCurrentWinner().getUserId());
    }

    // ================================================================
    // NHÓM 5: Anti-sniping — BVA quanh ngưỡng 30 giây
    // BVA: 31s còn lại (không trigger), 30s (boundary), 15s (trigger rõ)
    // ================================================================

    @Test @DisplayName("[BVA] Còn 31 giây — anti-sniping KHÔNG trigger (trên ngưỡng)")
    void testAntiSnipe_31SecondsLeft_BVA() throws InvalidBidException {
        AuctionRoom r = new AuctionRoom(4, 99, item,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusSeconds(31));
        LocalDateTime original = r.getEndTime();
        r.placeBid(bidderA, new BigDecimal("1500"));
        assertEquals(original, r.getEndTime(), "Không được gia hạn khi còn > 30s");
    }

    @Test @DisplayName("[BVA] Còn 30 giây đúng — anti-sniping TRIGGER (tại ngưỡng)")
    void testAntiSnipe_30SecondsLeft_BVA() throws InvalidBidException {
        AuctionRoom r = new AuctionRoom(5, 99, item,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusSeconds(30));
        LocalDateTime original = r.getEndTime();
        r.placeBid(bidderA, new BigDecimal("1500"));
        // endTime phải được gia hạn thêm 60 giây tính từ now
        assertTrue(r.getEndTime().isAfter(original),
                "Phải gia hạn khi bid xảy ra trong 30s cuối");
    }

    @Test @DisplayName("[BVA] Còn 15 giây — anti-sniping TRIGGER rõ ràng")
    void testAntiSnipe_15SecondsLeft_BVA() throws InvalidBidException {
        LocalDateTime close = LocalDateTime.now().plusSeconds(15);
        AuctionRoom r = new AuctionRoom(6, 99, item,
                LocalDateTime.now().minusMinutes(5), close);
        r.placeBid(bidderA, new BigDecimal("1500"));
        assertTrue(r.getEndTime().isAfter(close), "endTime phải được gia hạn");
    }

    @Test @DisplayName("[EP] Phòng còn 1 giờ — anti-sniping KHÔNG trigger")
    void testAntiSnipe_1HourLeft_EP() throws InvalidBidException {
        LocalDateTime original = room.getEndTime();
        room.placeBid(bidderA, new BigDecimal("1500"));
        assertEquals(original, room.getEndTime());
    }

    @Test @DisplayName("[BVA] Anti-sniping đạt tối đa 5 lần — không gia hạn lần 6")
    void testAntiSnipe_MaxExtensions_BVA() throws InvalidBidException {
        AuctionRoom r = new AuctionRoom(7, 99, item,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusSeconds(10));
        // Đặt giá 6 lần trong vùng trigger
        for (int i = 1; i <= 6; i++) {
            r.placeBid(bidderA, new BigDecimal(1000 + i * 100));
        }
        // Tối đa 5 lần × 60s = 300s; cộng thêm sai số 2s
        LocalDateTime maxAllowed = LocalDateTime.now().plusSeconds(10 + 300 + 2);
        assertTrue(r.getEndTime().isBefore(maxAllowed),
                "Không được gia hạn quá 5 lần");
    }

    // ================================================================
    // NHÓM 6: Auto-bid — EP + BVA
    // EP: max đủ / max không đủ số dư / step bình thường / step vượt max (All-In)
    // ================================================================

    @Test @DisplayName("[EP] Đăng ký AutoBid khi số dư đủ — không ném exception")
    void testAutoBid_Register_Valid_EP() {
        assertDoesNotThrow(
                () -> room.registerAutoBid(bidderA, new BigDecimal("5000"), new BigDecimal("500")));
    }

    @Test @DisplayName("[EP] Đăng ký AutoBid khi max > số dư — ném InvalidBidException")
    void testAutoBid_Register_InsufficientBalance_EP() {
        assertThrows(InvalidBidException.class,
                () -> room.registerAutoBid(bidderB, new BigDecimal("100000"), new BigDecimal("500")));
    }

    @Test @DisplayName("[EP] AutoBid bước thường — tự động đặt currentPrice + step")
    void testAutoBid_NormalStep_EP() throws InvalidBidException {
        // [EP] placeBid() KHÔNG kích hoạt AutoBid — chỉ registerAutoBid() mới gọi processInMemoryAutoBids().
        //
        // Flow khi Bob registerAutoBid(max=5000, step=500):
        //   next = currentPrice(1000) + step(500) = 1500 ≤ max(5000) → [Step] Bob → 1500
        //   Vòng lặp tiếp: Bob là winner → skip → dừng
        // Kết quả: price=1500, winner=Bob
        room.registerAutoBid(bidderB, new BigDecimal("5000"), new BigDecimal("500"));

        assertEquals(new BigDecimal("1500"), room.getCurrentPrice());
        assertEquals(bidderB.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test @DisplayName("[EP] AutoBid All-In — step vượt max, đặt đúng max")
    void testAutoBid_AllIn_EP() throws InvalidBidException {
        // [EP] All-In xảy ra khi next = currentPrice + step > maxBid NHƯNG maxBid > currentPrice.
        //
        // Flow khi Bob registerAutoBid(max=1700, step=1000):
        //   next = 1000 + 1000 = 2000 > max(1700) → không Step được
        //   canWin = 1700 > 1000 = true → [ALL-IN] Bob → 1700
        //   Vòng lặp: Bob là winner → skip → dừng
        // Kết quả: price=1700, winner=Bob
        room.registerAutoBid(bidderB, new BigDecimal("1700"), new BigDecimal("1000"));

        assertEquals(new BigDecimal("1700"), room.getCurrentPrice());
        assertEquals(bidderB.getUserId(), room.getCurrentWinner().getUserId());
        // Giá 1700 chứng tỏ đây là ALL-IN (không phải step thông thường 1000+1000=2000)
        assertTrue(room.getCurrentPrice().compareTo(new BigDecimal("1000")) > 0);
        assertTrue(room.getCurrentPrice().compareTo(new BigDecimal("2000")) < 0);
    }

    @Test @DisplayName("[EP] AutoBid bị vượt qua — Alice đặt cao hơn max của Bob")
    void testAutoBid_Defeated_EP() throws InvalidBidException {
        // Bob max = 2000, Alice đặt 3000 → Bob không thể counter
        room.registerAutoBid(bidderB, new BigDecimal("2000"), new BigDecimal("200"));
        room.placeBid(bidderA, new BigDecimal("3000"));
        assertEquals(bidderA.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test @DisplayName("[EP] AutoBid vs AutoBid — người có max cao hơn thắng")
    void testAutoBid_VsAutoBid_EP() throws InvalidBidException {
        room.registerAutoBid(bidderB, new BigDecimal("3000"), new BigDecimal("200"));
        room.registerAutoBid(bidderC, new BigDecimal("4000"), new BigDecimal("200"));
        // Charlie max 4000 > Bob max 3000 → Charlie thắng
        assertEquals(bidderC.getUserId(), room.getCurrentWinner().getUserId());
        assertTrue(room.getCurrentPrice().compareTo(new BigDecimal("3000")) >= 0);
    }

    @Test @DisplayName("[EP] AutoBid Tie-breaker — max bằng nhau, người đăng ký trước thắng")
    void testAutoBid_TieBreaker_EP() throws InvalidBidException, InterruptedException {
        room.registerAutoBid(bidderB, new BigDecimal("3000"), new BigDecimal("200"));
        Thread.sleep(50); // đảm bảo registerTime khác nhau
        room.registerAutoBid(bidderC, new BigDecimal("3000"), new BigDecimal("200"));
        // Bob đăng ký trước → Bob thắng theo tie-breaker
        assertEquals(bidderB.getUserId(), room.getCurrentWinner().getUserId());
        assertEquals(new BigDecimal("3000"), room.getCurrentPrice());
    }

    /**
     * [BVA] Max bid đúng bằng số dư — boundary hợp lệ
     */
    @Test @DisplayName("[BVA] AutoBid max = đúng bằng số dư — hợp lệ (boundary)")
    void testAutoBid_MaxExactlyBalance_BVA() {
        // bidderA có 50000, max = 50000 → hợp lệ
        assertDoesNotThrow(
                () -> room.registerAutoBid(bidderA, new BigDecimal("50000"), new BigDecimal("1000")));
    }

    @Test @DisplayName("[BVA] AutoBid max = số dư + 0.01 — không hợp lệ (boundary min-)")
    void testAutoBid_MaxOnePennyOverBalance_BVA() {
        // bidderA có 50000, max = 50000.01 → vượt số dư
        assertThrows(InvalidBidException.class,
                () -> room.registerAutoBid(bidderA, new BigDecimal("50000.01"), new BigDecimal("1000")));
    }

    // ================================================================
    // NHÓM 7: 2-way Pairwise — kết hợp (trạng thái phòng × loại giá) (slide 22-24)
    // trạng thái phòng: {RUNNING, OPEN, EXPIRED}
    // loại giá        : {hợp lệ (> current), bằng current, thấp hơn current}
    //
    // TC | phòng   | giá           | kết quả mong đợi
    //  1 | RUNNING | hợp lệ        | PASS
    //  2 | RUNNING | bằng current  | FAIL (InvalidBidException)
    //  3 | RUNNING | thấp hơn      | FAIL (InvalidBidException)
    //  4 | OPEN    | hợp lệ        | FAIL (chưa chạy)
    //  5 | OPEN    | thấp hơn      | FAIL (chưa chạy)
    //  6 | EXPIRED | hợp lệ        | FAIL (đã hết giờ)
    // ================================================================

    @Test @DisplayName("[2-way] RUNNING + giá hợp lệ → PASS")
    void testPairwise_Running_ValidPrice() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("1500"));
        assertEquals(new BigDecimal("1500"), room.getCurrentPrice());
    }

    @Test @DisplayName("[2-way] RUNNING + giá bằng current → FAIL")
    void testPairwise_Running_EqualPrice() {
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(bidderA, new BigDecimal("1000")));
    }

    @Test @DisplayName("[2-way] RUNNING + giá thấp hơn current → FAIL")
    void testPairwise_Running_LowPrice() {
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(bidderA, new BigDecimal("500")));
    }

    @Test @DisplayName("[2-way] OPEN + giá hợp lệ → FAIL (phòng chưa chạy)")
    void testPairwise_Open_ValidPrice() {
        AuctionRoom openRoom = new AuctionRoom(10, 99, item,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2));
        assertThrows(InvalidBidException.class,
                () -> openRoom.placeBid(bidderA, new BigDecimal("1500")));
    }

    @Test @DisplayName("[2-way] OPEN + giá thấp → FAIL (phòng chưa chạy)")
    void testPairwise_Open_LowPrice() {
        AuctionRoom openRoom = new AuctionRoom(11, 99, item,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2));
        assertThrows(InvalidBidException.class,
                () -> openRoom.placeBid(bidderA, new BigDecimal("500")));
    }

    @Test @DisplayName("[2-way] EXPIRED + giá hợp lệ → FAIL (phòng đã hết giờ)")
    void testPairwise_Expired_ValidPrice() {
        AuctionRoom expired = new AuctionRoom(12, 99, item,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusSeconds(1));
        expired.setStatus(AuctionStatus.RUNNING);
        assertThrows(InvalidBidException.class,
                () -> expired.placeBid(bidderA, new BigDecimal("1500")));
    }
}