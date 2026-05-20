import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.models.auction.AuctionStatus;
import server.utils.Validation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationTest — thiết kế theo giáo trình UET.CS2043
 * [EP]  = Equivalence Partitioning  [BVA] = Boundary Value Analysis
 * [EG]  = Error Guessing            [@BeforeEach] = lifecycle (slide 38)
 *
 * Thay đổi so với phiên bản cũ:
 *   - Xóa NHÓM 1 (validateEmail): method đã bị xóa khỏi Validation.
 *   - Xóa NHÓM 4 (validateBidAmount): method đã bị xóa khỏi Validation
 *     (logic nằm trong PayloadParser + AuctionService).
 *   - Xóa NHÓM 5 (validatePaymentAbility): method đã bị xóa khỏi Validation
 *     (logic nằm trong User.hasEnoughBalance() và AuctionService).
 *   - Cập nhật NHÓM 2 (validateUsername): regex mới ^[A-Za-z0-9_]{3,30}$
 *     → username 1-2 ký tự, có ký tự đặc biệt đều không hợp lệ.
 *   - Cập nhật NHÓM 5 (canTransitionTo): RUNNING → CANCELED nay HỢP LỆ.
 *   - Constructor Bidder bỏ tham số email.
 */
public class ValidationTest {

    // ================================================================
    // NHÓM 1: validatePassword (yêu cầu >= 6 ký tự)
    // EP: lớp >= 6 ký tự / lớp < 6 ký tự
    // BVA: length = 5 (min-), 6 (min), 7 (min+), 0 (rỗng)
    // ================================================================

    @Test @DisplayName("[EP] Password hợp lệ — đại diện lớp >= 6")
    void testPassword_Valid_EP() {
        assertDoesNotThrow(() -> Validation.validatePassword("abcdef"));
        assertDoesNotThrow(() -> Validation.validatePassword("Str0ng@Pass!"));
    }

    @Test @DisplayName("[EP] Password quá ngắn — đại diện lớp < 6")
    void testPassword_TooShort_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validatePassword("abc"));
    }

    @Test @DisplayName("[BVA] Password 5 ký tự — boundary min- (không hợp lệ)")
    void testPassword_5Chars_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validatePassword("12345"));
    }

    @Test @DisplayName("[BVA] Password 6 ký tự — boundary min (hợp lệ)")
    void testPassword_6Chars_BVA() {
        assertDoesNotThrow(() -> Validation.validatePassword("123456"));
    }

    @Test @DisplayName("[BVA] Password 7 ký tự — boundary min+ (hợp lệ)")
    void testPassword_7Chars_BVA() {
        assertDoesNotThrow(() -> Validation.validatePassword("1234567"));
    }

    @Test @DisplayName("[BVA] Password rỗng — boundary dưới cùng")
    void testPassword_Empty_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validatePassword(""));
    }

    @Test @DisplayName("[BVA] Password null")
    void testPassword_Null_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validatePassword(null));
    }

    // ================================================================
    // NHÓM 2: validateUsername — regex ^[A-Za-z0-9_]{3,30}$
    // EP: lớp hợp lệ (chữ/số/underscore, 3-30 ký tự) /
    //     lớp quá ngắn (<3) / lớp quá dài (>30) /
    //     lớp ký tự đặc biệt / lớp rỗng-null
    // BVA: length = 2 (min-), 3 (min), 4 (min+), 30 (max), 31 (max+)
    //      space ở đầu/giữa/cuối
    // ================================================================

    @Test @DisplayName("[EP] Username hợp lệ — chữ, số, underscore")
    void testUsername_Valid_EP() {
        assertDoesNotThrow(() -> Validation.validateUsername("alice123"));
        assertDoesNotThrow(() -> Validation.validateUsername("user_name"));
        assertDoesNotThrow(() -> Validation.validateUsername("ABC"));
    }

    @Test @DisplayName("[EP] Username rỗng — không hợp lệ")
    void testUsername_Empty_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername(""));
    }

    @Test @DisplayName("[EP] Username null — không hợp lệ")
    void testUsername_Null_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername(null));
    }

    @Test @DisplayName("[EP] Username có khoảng trắng ở giữa — không hợp lệ")
    void testUsername_SpaceMiddle_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername("alice bob"));
    }

    @Test @DisplayName("[EP] Username có ký tự đặc biệt — không hợp lệ")
    void testUsername_SpecialChar_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername("alice@123"));
    }

    @Test @DisplayName("[BVA] Username 2 ký tự — boundary min- (không hợp lệ, cần >= 3)")
    void testUsername_2Chars_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername("ab"));
    }

    @Test @DisplayName("[BVA] Username 3 ký tự — boundary min (hợp lệ nhỏ nhất)")
    void testUsername_3Chars_BVA() {
        assertDoesNotThrow(() -> Validation.validateUsername("abc"));
    }

    @Test @DisplayName("[BVA] Username 4 ký tự — boundary min+ (hợp lệ)")
    void testUsername_4Chars_BVA() {
        assertDoesNotThrow(() -> Validation.validateUsername("abcd"));
    }

    @Test @DisplayName("[BVA] Username 30 ký tự — boundary max (hợp lệ)")
    void testUsername_30Chars_BVA() {
        // 30 ký tự: hợp lệ tối đa
        assertDoesNotThrow(() -> Validation.validateUsername("a".repeat(30)));
    }

    @Test @DisplayName("[BVA] Username 31 ký tự — boundary max+ (không hợp lệ)")
    void testUsername_31Chars_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername("a".repeat(31)));
    }

    @Test @DisplayName("[BVA] Space ở đầu username — boundary")
    void testUsername_SpaceAtStart_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername(" alice"));
    }

    @Test @DisplayName("[BVA] Space ở cuối username — boundary")
    void testUsername_SpaceAtEnd_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername("alice "));
    }

    @Test @DisplayName("[EG] Username chỉ gồm underscore — hợp lệ nếu đủ ký tự")
    void testUsername_OnlyUnderscores_EG() {
        // [EG] Edge case: "_" * 3 vẫn match regex
        assertDoesNotThrow(() -> Validation.validateUsername("___"));
    }

    // ================================================================
    // NHÓM 3: canTransitionTo — chuyển trạng thái phiên đấu giá
    //
    // Thay đổi so với phiên bản cũ:
    //   RUNNING → CANCELED: nay HỢP LỆ (Admin hoặc Seller hủy phòng đang chạy
    //   mà chưa có winner).
    //   RUNNING → OPEN (quay lui): vẫn không hợp lệ.
    //
    // EP: phân vùng theo từng trạng thái nguồn
    //   OPEN     → {RUNNING, CANCELED} hợp lệ; {FINISHED, PAID} không hợp lệ
    //   RUNNING  → {FINISHED, CANCELED} hợp lệ; {OPEN, PAID} không hợp lệ
    //   FINISHED → {PAID, CANCELED} hợp lệ; {OPEN, RUNNING} không hợp lệ
    //   PAID     → không chuyển được (terminal)
    //   CANCELED → không chuyển được (terminal)
    // ================================================================

    @Test @DisplayName("[EP] OPEN → RUNNING hợp lệ")
    void testTransition_OpenToRunning() {
        assertTrue(Validation.canTransitionTo(AuctionStatus.OPEN, AuctionStatus.RUNNING));
    }

    @Test @DisplayName("[EP] OPEN → CANCELED hợp lệ")
    void testTransition_OpenToCanceled() {
        assertTrue(Validation.canTransitionTo(AuctionStatus.OPEN, AuctionStatus.CANCELED));
    }

    @Test @DisplayName("[EP] OPEN → FINISHED không hợp lệ (nhảy cóc)")
    void testTransition_OpenToFinished() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.OPEN, AuctionStatus.FINISHED));
    }

    @Test @DisplayName("[EP] OPEN → PAID không hợp lệ (nhảy cóc)")
    void testTransition_OpenToPaid() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.OPEN, AuctionStatus.PAID));
    }

    @Test @DisplayName("[EP] RUNNING → FINISHED hợp lệ")
    void testTransition_RunningToFinished() {
        assertTrue(Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.FINISHED));
    }

    @Test @DisplayName("[EP] RUNNING → CANCELED hợp lệ (Admin/Seller hủy phòng đang chạy)")
    void testTransition_RunningToCanceled() {
        // Thay đổi: phiên bản cũ test assertFalse → nay RUNNING→CANCELED hợp lệ
        assertTrue(Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.CANCELED));
    }

    @Test @DisplayName("[EP] RUNNING → PAID không hợp lệ (bỏ qua FINISHED)")
    void testTransition_RunningToPaid() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.PAID));
    }

    @Test @DisplayName("[EP] RUNNING → OPEN không hợp lệ (quay lui)")
    void testTransition_RunningToOpen() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.OPEN));
    }

    @Test @DisplayName("[EP] FINISHED → PAID hợp lệ")
    void testTransition_FinishedToPaid() {
        assertTrue(Validation.canTransitionTo(AuctionStatus.FINISHED, AuctionStatus.PAID));
    }

    @Test @DisplayName("[EP] FINISHED → CANCELED hợp lệ")
    void testTransition_FinishedToCanceled() {
        assertTrue(Validation.canTransitionTo(AuctionStatus.FINISHED, AuctionStatus.CANCELED));
    }

    @Test @DisplayName("[EP] FINISHED → RUNNING không hợp lệ (quay lui)")
    void testTransition_FinishedToRunning() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.FINISHED, AuctionStatus.RUNNING));
    }

    @Test @DisplayName("[EP] FINISHED → OPEN không hợp lệ (quay lui)")
    void testTransition_FinishedToOpen() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.FINISHED, AuctionStatus.OPEN));
    }

    @Test @DisplayName("[EP] PAID → bất kỳ đều không hợp lệ (trạng thái terminal)")
    void testTransition_PaidIsTerminal() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.PAID, AuctionStatus.RUNNING));
        assertFalse(Validation.canTransitionTo(AuctionStatus.PAID, AuctionStatus.FINISHED));
        assertFalse(Validation.canTransitionTo(AuctionStatus.PAID, AuctionStatus.CANCELED));
        assertFalse(Validation.canTransitionTo(AuctionStatus.PAID, AuctionStatus.OPEN));
    }

    @Test @DisplayName("[EP] CANCELED → bất kỳ đều không hợp lệ (trạng thái terminal)")
    void testTransition_CanceledIsTerminal() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.CANCELED, AuctionStatus.OPEN));
        assertFalse(Validation.canTransitionTo(AuctionStatus.CANCELED, AuctionStatus.RUNNING));
        assertFalse(Validation.canTransitionTo(AuctionStatus.CANCELED, AuctionStatus.PAID));
        assertFalse(Validation.canTransitionTo(AuctionStatus.CANCELED, AuctionStatus.FINISHED));
    }

    @Test @DisplayName("[EG] canTransitionTo với null — trả false an toàn")
    void testTransition_NullArgs_EG() {
        assertFalse(Validation.canTransitionTo(null, AuctionStatus.RUNNING));
        assertFalse(Validation.canTransitionTo(AuctionStatus.OPEN, null));
        assertFalse(Validation.canTransitionTo(null, null));
    }
}