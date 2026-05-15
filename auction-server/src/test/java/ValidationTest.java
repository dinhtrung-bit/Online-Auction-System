import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.exceptions.InvalidBidException;
import server.models.auction.AuctionStatus;
import server.models.users.Bidder;
import server.utils.Validation;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationTest — thiết kế theo giáo trình UET.CS2043
 * [EP]  = Equivalence Partitioning  [BVA] = Boundary Value Analysis
 * [EG]  = Error Guessing            [@BeforeEach] = lifecycle (slide 38)
 */
public class ValidationTest {

    private Bidder richBidder;
    private Bidder poorBidder;
    private Bidder zeroBidder;

    // @BeforeEach: khởi tạo dữ liệu dùng chung trước mỗi test (slide 38)
    @BeforeEach
    void setUp() {
        richBidder = new Bidder(1, "Alice", "pw", "alice@mail.com", new BigDecimal("10000"));
        poorBidder = new Bidder(2, "Bob",   "pw", "bob@mail.com",   new BigDecimal("100"));
        zeroBidder = new Bidder(3, "Zero",  "pw", "zero@mail.com",  new BigDecimal("0"));
    }

    // ================================================================
    // NHÓM 1: validateEmail
    // EP: lớp hợp lệ / thiếu '@' / rỗng / null
    // BVA: '@' ở đầu chuỗi, '@' ở cuối chuỗi, '@' ở giữa (chuẩn)
    // ================================================================

    @Test @DisplayName("[EP] Email hợp lệ")
    void testEmail_Valid_EP() {
        assertDoesNotThrow(() -> Validation.validateEmail("user@example.com"));
        assertDoesNotThrow(() -> Validation.validateEmail("a.b+tag@sub.domain.vn"));
    }

    @Test @DisplayName("[EP] Email thiếu '@'")
    void testEmail_MissingAt_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateEmail("invalidemail.com"));
    }

    @Test @DisplayName("[EP] Email rỗng")
    void testEmail_Empty_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateEmail(""));
    }

    @Test @DisplayName("[EP] Email null")
    void testEmail_Null_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateEmail(null));
    }

    @Test @DisplayName("[BVA] '@' ở đầu chuỗi — boundary min")
    void testEmail_AtStart_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateEmail("@nodomain"));
    }

    @Test @DisplayName("[BVA] '@' ở cuối chuỗi — boundary max")
    void testEmail_AtEnd_BVA() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateEmail("nouser@"));
    }

    @Test @DisplayName("[EG] Email chứa khoảng trắng — lỗi thực tế phổ biến")
    void testEmail_WithSpace_EG() {
        // [EG] Đoán lỗi: input người dùng hay nhập email có dấu cách
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateEmail("user name@example.com"));
    }

    // ================================================================
    // NHÓM 2: validatePassword (yêu cầu >= 6 ký tự)
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
    // NHÓM 3: validateUsername
    // EP: lớp hợp lệ / lớp rỗng-null / lớp có khoảng trắng
    // BVA: space ở đầu, space ở cuối, space ở giữa
    // ================================================================

    @Test @DisplayName("[EP] Username hợp lệ")
    void testUsername_Valid_EP() {
        assertDoesNotThrow(() -> Validation.validateUsername("alice123"));
        assertDoesNotThrow(() -> Validation.validateUsername("user_name"));
    }

    @Test @DisplayName("[EP] Username rỗng — lớp không hợp lệ (1)")
    void testUsername_Empty_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername(""));
    }

    @Test @DisplayName("[EP] Username null")
    void testUsername_Null_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername(null));
    }

    @Test @DisplayName("[EP] Username có khoảng trắng ở giữa — lớp không hợp lệ (2)")
    void testUsername_SpaceMiddle_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateUsername("alice bob"));
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

    // ================================================================
    // NHÓM 4: validateBidAmount
    // EP: lớp dương / lớp âm-hoặc-0 / lớp không phải số / lớp rỗng-null
    // BVA: -0.01 (âm sát 0), 0 (zero), 0.01 (dương nhỏ nhất)
    // ================================================================

    @Test @DisplayName("[EP] Số tiền hợp lệ — đại diện lớp dương")
    void testBidAmount_Valid_EP() throws InvalidBidException {
        BigDecimal r = Validation.validateBidAmount("1500.50");
        assertEquals(new BigDecimal("1500.50"), r);
    }

    @Test @DisplayName("[EP] Số tiền âm — đại diện lớp không hợp lệ (1)")
    void testBidAmount_Negative_EP() {
        assertThrows(InvalidBidException.class,
                () -> Validation.validateBidAmount("-500"));
    }

    @Test @DisplayName("[EP] Chuỗi không phải số — đại diện lớp không hợp lệ (2)")
    void testBidAmount_NotNumber_EP() {
        assertThrows(InvalidBidException.class,
                () -> Validation.validateBidAmount("abc"));
    }

    @Test @DisplayName("[EP] Rỗng — đại diện lớp không hợp lệ (3)")
    void testBidAmount_Empty_EP() {
        assertThrows(InvalidBidException.class,
                () -> Validation.validateBidAmount(""));
    }

    @Test @DisplayName("[EP] Null — đại diện lớp không hợp lệ (3)")
    void testBidAmount_Null_EP() {
        assertThrows(InvalidBidException.class,
                () -> Validation.validateBidAmount(null));
    }

    @Test @DisplayName("[BVA] Số tiền = 0 — boundary (không hợp lệ)")
    void testBidAmount_Zero_BVA() {
        assertThrows(InvalidBidException.class,
                () -> Validation.validateBidAmount("0"));
    }

    @Test @DisplayName("[BVA] Số tiền = 0.01 — boundary min+ (hợp lệ nhỏ nhất)")
    void testBidAmount_SmallestPositive_BVA() throws InvalidBidException {
        BigDecimal r = Validation.validateBidAmount("0.01");
        assertTrue(r.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test @DisplayName("[BVA] Số tiền = -0.01 — boundary âm sát 0 (không hợp lệ)")
    void testBidAmount_SmallestNegative_BVA() {
        assertThrows(InvalidBidException.class,
                () -> Validation.validateBidAmount("-0.01"));
    }

    @Test @DisplayName("[EG] Số tiền có khoảng trắng hai đầu — lỗi input thực tế")
    void testBidAmount_Whitespace_EG() throws InvalidBidException {
        // [EG] Người dùng thường vô tình thêm space; hàm .trim() phải xử lý được
        BigDecimal r = Validation.validateBidAmount("  5000  ");
        assertEquals(new BigDecimal("5000"), r);
    }

    // ================================================================
    // NHÓM 5: validatePaymentAbility
    // EP: lớp đủ tiền (balance >= amount) / lớp thiếu tiền (balance < amount)
    // BVA: balance == amount (đúng bằng), balance = amount - 0.01 (thiếu 1 xu)
    // ================================================================

    @Test @DisplayName("[EP] Đủ tiền — đại diện lớp hợp lệ")
    void testPayment_Sufficient_EP() {
        assertDoesNotThrow(
                () -> Validation.validatePaymentAbility(richBidder, new BigDecimal("5000")));
    }

    @Test @DisplayName("[EP] Thiếu tiền — đại diện lớp không hợp lệ")
    void testPayment_Insufficient_EP() {
        Exception ex = assertThrows(Exception.class,
                () -> Validation.validatePaymentAbility(poorBidder, new BigDecimal("5000")));
        assertTrue(ex.getMessage().contains("Số dư tài khoản hiện tại không đủ"));
    }

    @Test @DisplayName("[BVA] Số dư đúng bằng số tiền cần — boundary (hợp lệ)")
    void testPayment_ExactBalance_BVA() {
        // balance = 10000, amount = 10000 → compareTo = 0 → không throw
        assertDoesNotThrow(
                () -> Validation.validatePaymentAbility(richBidder, new BigDecimal("10000")));
    }

    @Test @DisplayName("[BVA] Thiếu đúng 0.01 — boundary min- (không hợp lệ)")
    void testPayment_OnePennyShort_BVA() {
        // poorBidder có 100, amount = 100.01 → thiếu 0.01
        assertThrows(Exception.class,
                () -> Validation.validatePaymentAbility(poorBidder, new BigDecimal("100.01")));
    }

    @Test @DisplayName("[BVA] Số dư = 0, amount bất kỳ > 0 — boundary dưới cùng")
    void testPayment_ZeroBalance_BVA() {
        assertThrows(Exception.class,
                () -> Validation.validatePaymentAbility(zeroBidder, new BigDecimal("0.01")));
    }

    // ================================================================
    // NHÓM 6: canTransitionTo — chuyển trạng thái phiên đấu giá
    // EP: phân vùng theo từng trạng thái nguồn
    //   OPEN     → {RUNNING, CANCELED} hợp lệ
    //   RUNNING  → {FINISHED} hợp lệ
    //   FINISHED → {PAID, CANCELED} hợp lệ
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

    @Test @DisplayName("[EP] RUNNING → PAID không hợp lệ (bỏ qua FINISHED)")
    void testTransition_RunningToPaid() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.PAID));
    }

    @Test @DisplayName("[EP] RUNNING → CANCELED không hợp lệ")
    void testTransition_RunningToCanceled() {
        assertFalse(Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.CANCELED));
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
}