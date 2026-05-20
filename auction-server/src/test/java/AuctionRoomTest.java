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
 *
 * Thay đổi so với phiên bản cũ:
 *   - Constructor Bidder/Seller/Admin đã bỏ tham số email.
 *   - NHÓM 6 (AutoBid): registerAutoBid() đã bị xóa khỏi AuctionRoom (chuyển vào
 *     AuctionService). Test được viết lại để test applyAutoBid() trực tiếp —
 *     đây là method domain model thực sự còn tồn tại sau refactor.
 */
public class AuctionRoomTest {

    private Item item;
    private AuctionRoom room;
    private Bidder bidderA;
    private Bidder bidderB;
    private Bidder bidderC;

    @BeforeEach
    void setUp() {
        item    = new Art(1, "Mona Lisa Copy", new BigDecimal("1000"), "Bức tranh test");
        bidderA = new Bidder(1, "Alice",   "pw", new BigDecimal("50000"));
        bidderB = new Bidder(2, "Bob",     "pw", new BigDecimal("50000"));
        bidderC = new Bidder(3, "Charlie", "pw", new BigDecimal("50000"));
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

    @Test @DisplayName("[EP] Đặt giá khi phòng đã hết giờ (EXPIRED) — bị từ chối và status = FINISHED")
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
        Exception ex = assertThrows(InvalidBidException.class,
                () -> room.placeBid(bidderB, new BigDecimal("1999")));
        assertTrue(ex.getMessage().contains("2000"));
    }

    // ================================================================
    // NHÓM 3: Số dư người đặt giá — EP + BVA
    // ================================================================

    @Test @DisplayName("[EP] Số dư không đủ — bị từ chối")
    void testBid_InsufficientBalance_EP() {
        Bidder poorBidder = new Bidder(4, "Poor", "pw", new BigDecimal("500"));
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(poorBidder, new BigDecimal("1500")));
    }

    @Test @DisplayName("[BVA] Số dư đúng bằng giá đặt — hợp lệ (boundary)")
    void testBid_BalanceExactlyEnough_BVA() {
        Bidder exactBidder = new Bidder(5, "Exact", "pw", new BigDecimal("1500"));
        assertDoesNotThrow(() -> room.placeBid(exactBidder, new BigDecimal("1500")));
    }

    @Test @DisplayName("[BVA] Số dư ít hơn giá đặt 0.01 — không hợp lệ (boundary min-)")
    void testBid_BalanceOnePennyShort_BVA() {
        Bidder almostBidder = new Bidder(6, "Almost", "pw", new BigDecimal("1499.99"));
        assertThrows(InvalidBidException.class,
                () -> room.placeBid(almostBidder, new BigDecimal("1500")));
    }

    // ================================================================
    // NHÓM 4: Lịch sử đặt giá — kiểm tra state sau bid
    // ================================================================

    @Test @DisplayName("[EP] Sau 1 bid hợp lệ — winner và giá được cập nhật đúng")
    void testBid_StateAfterOneBid_EP() throws InvalidBidException {
        assertNull(room.getCurrentWinner());
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

    @Test @DisplayName("[EP] Bid history tăng đúng sau mỗi lượt bid")
    void testBid_HistoryGrows_EP() throws InvalidBidException {
        assertEquals(0, room.getBidHistory().size());
        room.placeBid(bidderA, new BigDecimal("1500"));
        assertEquals(1, room.getBidHistory().size());
        room.placeBid(bidderB, new BigDecimal("2000"));
        assertEquals(2, room.getBidHistory().size());
    }

    @Test @DisplayName("[EG] Cùng người đặt giá hai lần liên tiếp — hợp lệ nếu giá sau > giá trước")
    void testBid_SamePersonBidTwice_EG() throws InvalidBidException {
        room.placeBid(bidderA, new BigDecimal("1500"));
        room.placeBid(bidderA, new BigDecimal("2000"));
        assertEquals(new BigDecimal("2000"), room.getCurrentPrice());
        assertEquals(bidderA.getUserId(), room.getCurrentWinner().getUserId());
    }

    // ================================================================
    // NHÓM 5: Anti-sniping — BVA quanh ngưỡng 30 giây
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
        for (int i = 1; i <= 6; i++) {
            r.placeBid(bidderA, new BigDecimal(1000 + i * 100));
        }
        // Tối đa 5 lần × 60s = 300s; cộng sai số 2s
        LocalDateTime maxAllowed = LocalDateTime.now().plusSeconds(10 + 300 + 2);
        assertTrue(r.getEndTime().isBefore(maxAllowed),
                "Không được gia hạn quá 5 lần");
    }

    // ================================================================
    // NHÓM 6: applyAutoBid — EP + BVA
    //
    // Thay đổi so với phiên bản cũ:
    //   registerAutoBid() đã bị xóa khỏi AuctionRoom. AuctionService là nơi
    //   điều phối auto-bid logic (gọi DAO, chọn config, tính nextBid) rồi
    //   gọi room.applyAutoBid() để áp kết quả lên domain model.
    //
    //   Các test dưới đây kiểm tra applyAutoBid() trực tiếp — đây là contract
    //   giữa AuctionService và AuctionRoom:
    //     - Khi phòng RUNNING và amount > currentPrice → áp thành công
    //     - Khi phòng không RUNNING → ném InvalidBidException
    //     - Khi amount <= currentPrice → ném InvalidBidException
    //     - Bidder null → ném InvalidBidException
    //     - Anti-sniping vẫn được trigger bởi applyAutoBid (vì gọi applyBid nội bộ)
    // ================================================================

    @Test @DisplayName("[EP] applyAutoBid hợp lệ — cập nhật price và winner đúng")
    void testApplyAutoBid_Valid_EP() throws InvalidBidException {
        room.applyAutoBid(bidderA, new BigDecimal("2000"));
        assertEquals(new BigDecimal("2000"), room.getCurrentPrice());
        assertEquals(bidderA.getUserId(), room.getCurrentWinner().getUserId());
    }

    @Test @DisplayName("[BVA] applyAutoBid amount = currentPrice + 1 — boundary min+ hợp lệ")
    void testApplyAutoBid_OneAboveCurrent_BVA() throws InvalidBidException {
        room.applyAutoBid(bidderA, new BigDecimal("1001"));
        assertEquals(new BigDecimal("1001"), room.getCurrentPrice());
    }

    @Test @DisplayName("[BVA] applyAutoBid amount = currentPrice — boundary, không hợp lệ")
    void testApplyAutoBid_EqualCurrentPrice_BVA() {
        assertThrows(InvalidBidException.class,
                () -> room.applyAutoBid(bidderA, new BigDecimal("1000")));
    }

    @Test @DisplayName("[BVA] applyAutoBid amount < currentPrice — không hợp lệ")
    void testApplyAutoBid_BelowCurrentPrice_BVA() {
        assertThrows(InvalidBidException.class,
                () -> room.applyAutoBid(bidderA, new BigDecimal("500")));
    }

    @Test @DisplayName("[EP] applyAutoBid khi phòng OPEN — ném InvalidBidException")
    void testApplyAutoBid_WhenOpen_EP() {
        AuctionRoom openRoom = new AuctionRoom(20, 99, item,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2));
        assertThrows(InvalidBidException.class,
                () -> openRoom.applyAutoBid(bidderA, new BigDecimal("1500")));
    }

    @Test @DisplayName("[EP] applyAutoBid khi phòng EXPIRED — ném InvalidBidException")
    void testApplyAutoBid_WhenExpired_EP() {
        AuctionRoom expired = new AuctionRoom(21, 99, item,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusSeconds(1));
        expired.setStatus(AuctionStatus.RUNNING);
        assertThrows(InvalidBidException.class,
                () -> expired.applyAutoBid(bidderA, new BigDecimal("1500")));
    }

    @Test @DisplayName("[EG] applyAutoBid với bidder null — ném InvalidBidException")
    void testApplyAutoBid_NullBidder_EG() {
        assertThrows(InvalidBidException.class,
                () -> room.applyAutoBid(null, new BigDecimal("1500")));
    }

    @Test @DisplayName("[EP] applyAutoBid nhiều lần liên tiếp — winner và price luôn là lượt cuối")
    void testApplyAutoBid_Sequential_EP() throws InvalidBidException {
        room.applyAutoBid(bidderA, new BigDecimal("1500"));
        room.applyAutoBid(bidderB, new BigDecimal("2000"));
        room.applyAutoBid(bidderC, new BigDecimal("3000"));

        assertEquals(new BigDecimal("3000"), room.getCurrentPrice());
        assertEquals(bidderC.getUserId(), room.getCurrentWinner().getUserId());
        assertEquals(3, room.getBidHistory().size());
    }

    @Test @DisplayName("[EP] applyAutoBid trong 30s cuối — vẫn trigger anti-sniping")
    void testApplyAutoBid_TriggersAntiSnipe_EP() throws InvalidBidException {
        AuctionRoom r = new AuctionRoom(22, 99, item,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusSeconds(15));
        LocalDateTime before = r.getEndTime();
        r.applyAutoBid(bidderA, new BigDecimal("1500"));
        assertTrue(r.getEndTime().isAfter(before),
                "applyAutoBid phải trigger anti-sniping giống placeBid");
    }

    // ================================================================
    // NHÓM 7: 2-way Pairwise — kết hợp (trạng thái phòng × loại giá) (slide 22-24)
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
