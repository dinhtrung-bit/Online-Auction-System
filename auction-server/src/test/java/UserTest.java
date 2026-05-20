import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.models.users.Admin;
import server.models.users.Bidder;
import server.models.users.Seller;
import server.models.users.UserFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserTest — thiết kế theo giáo trình UET.CS2043
 * [EP]    = Equivalence Partitioning
 * [BVA]   = Boundary Value Analysis
 * [EG]    = Error Guessing
 * [2-way] = Pairwise / 2-way Combinatorial Testing (slide 20-24)
 *
 * Thay đổi so với phiên bản cũ:
 *   - Constructor Bidder/Seller/Admin bỏ tham số email.
 *   - updateBalance() đã đổi thành debit() (trừ tiền) và credit() (cộng tiền).
 *   - Thêm NHÓM 1b: kiểm tra credit() riêng.
 *   - Thêm NHÓM 1c: kiểm tra hasEnoughBalance().
 *   - NHÓM 5: canBid()/canSell()/canAdmin() thay cho kiểm tra instanceof.
 */
public class UserTest {

    private Bidder bidder;
    private Seller seller;

    @BeforeEach
    void setUp() {
        bidder = new Bidder(1, "alice", "hash", new BigDecimal("1000"));
        seller = new Seller(2, "bob",   "hash", new BigDecimal("0"));
    }

    // ================================================================
    // NHÓM 1: debit() — trừ tiền
    // EP: amount hợp lệ (balance sau >= 0) / amount quá mức (balance sau < 0)
    // BVA: ngưỡng 0 của số dư sau khi trừ
    // ================================================================

    @Test @DisplayName("[EP] Trừ tiền vừa đủ — hợp lệ, balance giảm đúng")
    void testDebit_Valid_EP() {
        assertTrue(bidder.debit(new BigDecimal("800")));
        assertEquals(new BigDecimal("200"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EP] Trừ quá số dư — không hợp lệ, balance giữ nguyên")
    void testDebit_TooMuch_EP() {
        assertFalse(bidder.debit(new BigDecimal("1500")));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[BVA] Trừ đúng bằng số dư — boundary (balance sau = 0, hợp lệ)")
    void testDebit_ExactBalance_BVA() {
        assertTrue(bidder.debit(new BigDecimal("1000")));
        assertEquals(0, bidder.getAccountBalance().compareTo(BigDecimal.ZERO));
    }

    @Test @DisplayName("[BVA] Trừ hơn số dư 0.01 — boundary min- (không hợp lệ)")
    void testDebit_OnePennyOver_BVA() {
        assertFalse(bidder.debit(new BigDecimal("1000.01")));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[BVA] Trừ amount = 0 — boundary zero (không thay đổi balance, trả false)")
    void testDebit_ZeroAmount_BVA() {
        // debit(0): amount <= 0 → return false ngay, balance không đổi
        assertFalse(bidder.debit(BigDecimal.ZERO));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[BVA] Trừ amount âm — không hợp lệ (trả false)")
    void testDebit_NegativeAmount_BVA() {
        assertFalse(bidder.debit(new BigDecimal("-100")));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EG] debit() nhiều lần liên tiếp — cộng dồn đúng")
    void testDebit_Multiple_EG() {
        bidder.debit(new BigDecimal("200"));
        bidder.debit(new BigDecimal("300"));
        bidder.debit(new BigDecimal("400"));
        assertEquals(new BigDecimal("100"), bidder.getAccountBalance());
    }

    // ================================================================
    // NHÓM 2: credit() — cộng tiền
    // EP: amount dương / amount = 0 / amount âm
    // BVA: cộng vào balance 0 (seller)
    // ================================================================

    @Test @DisplayName("[EP] Cộng tiền dương — balance tăng đúng")
    void testCredit_Positive_EP() {
        bidder.credit(new BigDecimal("500"));
        assertEquals(new BigDecimal("1500"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EP] Seller nhận tiền từ balance 0 — cộng đúng")
    void testCredit_FromZeroBalance_EP() {
        seller.credit(new BigDecimal("50000"));
        assertEquals(new BigDecimal("50000"), seller.getAccountBalance());
    }

    @Test @DisplayName("[BVA] Cộng amount = 0 — balance không thay đổi")
    void testCredit_ZeroAmount_BVA() {
        bidder.credit(BigDecimal.ZERO);
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[BVA] Cộng amount âm — credit() bỏ qua, balance không thay đổi")
    void testCredit_NegativeAmount_BVA() {
        bidder.credit(new BigDecimal("-200"));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EG] credit() nhiều lần — cộng dồn đúng")
    void testCredit_Multiple_EG() {
        bidder.credit(new BigDecimal("100"));
        bidder.credit(new BigDecimal("200"));
        bidder.credit(new BigDecimal("300"));
        assertEquals(new BigDecimal("1600"), bidder.getAccountBalance());
    }

    // ================================================================
    // NHÓM 3: hasEnoughBalance() — kiểm tra đủ tiền
    // EP: balance >= amount / balance < amount
    // BVA: balance == amount (đúng bằng), balance = amount + 0.01, balance = amount - 0.01
    // ================================================================

    @Test @DisplayName("[EP] hasEnoughBalance — đủ tiền rõ ràng")
    void testHasEnough_Sufficient_EP() {
        assertTrue(bidder.hasEnoughBalance(new BigDecimal("500")));
    }

    @Test @DisplayName("[EP] hasEnoughBalance — thiếu tiền rõ ràng")
    void testHasEnough_Insufficient_EP() {
        assertFalse(bidder.hasEnoughBalance(new BigDecimal("2000")));
    }

    @Test @DisplayName("[BVA] hasEnoughBalance — đúng bằng balance (boundary hợp lệ)")
    void testHasEnough_ExactBalance_BVA() {
        assertTrue(bidder.hasEnoughBalance(new BigDecimal("1000")));
    }

    @Test @DisplayName("[BVA] hasEnoughBalance — hơn balance 0.01 (boundary min-)")
    void testHasEnough_OnePennyOver_BVA() {
        assertFalse(bidder.hasEnoughBalance(new BigDecimal("1000.01")));
    }

    @Test @DisplayName("[BVA] hasEnoughBalance — kém balance 0.01 (boundary min+)")
    void testHasEnough_OnePennyUnder_BVA() {
        assertTrue(bidder.hasEnoughBalance(new BigDecimal("999.99")));
    }

    // ================================================================
    // NHÓM 4: canPlaceBid (Bidder) — gọi nội bộ hasEnoughBalance
    // EP: balance >= amount / balance < amount
    // BVA: balance == amount, balance = amount - 0.01
    // ================================================================

    @Test @DisplayName("[EP] canPlaceBid — đủ tiền")
    void testCanPlaceBid_Sufficient_EP() {
        assertTrue(bidder.canPlaceBid(new BigDecimal("500")));
    }

    @Test @DisplayName("[EP] canPlaceBid — thiếu tiền")
    void testCanPlaceBid_Insufficient_EP() {
        assertFalse(bidder.canPlaceBid(new BigDecimal("2000")));
    }

    @Test @DisplayName("[BVA] canPlaceBid — đúng bằng balance")
    void testCanPlaceBid_ExactBalance_BVA() {
        assertTrue(bidder.canPlaceBid(new BigDecimal("1000")));
    }

    @Test @DisplayName("[BVA] canPlaceBid — hơn balance 0.01")
    void testCanPlaceBid_OnePennyOver_BVA() {
        assertFalse(bidder.canPlaceBid(new BigDecimal("1000.01")));
    }

    // ================================================================
    // NHÓM 5: getRole() — Polymorphism
    // EP: mỗi role là 1 lớp tương đương riêng
    // ================================================================

    @Test @DisplayName("[EP] Bidder.getRole() = 'BIDDER'")
    void testRole_Bidder_EP() {
        assertEquals("BIDDER", bidder.getRole());
    }

    @Test @DisplayName("[EP] Seller.getRole() = 'SELLER'")
    void testRole_Seller_EP() {
        assertEquals("SELLER", seller.getRole());
    }

    @Test @DisplayName("[EP] Admin.getRole() = 'ADMIN'")
    void testRole_Admin_EP() {
        Admin admin = new Admin(3, "admin", "hash", BigDecimal.ZERO);
        assertEquals("ADMIN", admin.getRole());
    }

    // ================================================================
    // NHÓM 6: canBid() / canSell() / canAdmin() — quyền hạn (thay instanceof)
    // EP: mỗi subclass có đúng quyền của nó, không có quyền của role khác
    // ================================================================

    @Test @DisplayName("[EP] Bidder.canBid() = true")
    void testCapability_BidderCanBid_EP() {
        assertTrue(bidder.canBid());
    }

    @Test @DisplayName("[EP] Bidder.canSell() = false")
    void testCapability_BidderCannotSell_EP() {
        assertFalse(bidder.canSell());
    }

    @Test @DisplayName("[EP] Bidder.canAdmin() = false")
    void testCapability_BidderCannotAdmin_EP() {
        assertFalse(bidder.canAdmin());
    }

    @Test @DisplayName("[EP] Seller.canSell() = true")
    void testCapability_SellerCanSell_EP() {
        assertTrue(seller.canSell());
    }

    @Test @DisplayName("[EP] Seller.canBid() = false")
    void testCapability_SellerCannotBid_EP() {
        assertFalse(seller.canBid());
    }

    @Test @DisplayName("[EP] Admin.canAdmin() = true")
    void testCapability_AdminCanAdmin_EP() {
        Admin admin = new Admin(3, "admin", "hash", BigDecimal.ZERO);
        assertTrue(admin.canAdmin());
    }

    @Test @DisplayName("[EP] Admin.canBid() = false, canSell() = false")
    void testCapability_AdminHasNoOtherRoles_EP() {
        Admin admin = new Admin(3, "admin", "hash", BigDecimal.ZERO);
        assertFalse(admin.canBid());
        assertFalse(admin.canSell());
    }

    // ================================================================
    // NHÓM 7: UserFactory — Factory Method Pattern
    // EP: role hợp lệ {BIDDER, SELLER, ADMIN} / role không hợp lệ / null / rỗng
    // [2-way] Kết hợp 2 tham số: role × id (slide 20-24)
    // ================================================================

    @Test @DisplayName("[EP] Factory tạo Bidder thành công")
    void testFactory_Bidder_EP() {
        var user = UserFactory.createUser("BIDDER", 10, "tester");
        assertNotNull(user);
        assertEquals("BIDDER", user.getRole());
        assertEquals("tester", user.getUsername());
    }

    @Test @DisplayName("[EP] Factory tạo Seller thành công")
    void testFactory_Seller_EP() {
        var user = UserFactory.createUser("SELLER", 20, "shopowner");
        assertNotNull(user);
        assertEquals("SELLER", user.getRole());
    }

    @Test @DisplayName("[EP] Factory tạo Admin thành công")
    void testFactory_Admin_EP() {
        var user = UserFactory.createUser("ADMIN", 99, "sysadmin");
        assertNotNull(user);
        assertEquals("ADMIN", user.getRole());
    }

    @Test @DisplayName("[EP] Factory role viết thường — vẫn tạo được (case-insensitive)")
    void testFactory_LowercaseRole_EP() {
        var user = UserFactory.createUser("bidder", 5, "user5");
        assertNotNull(user);
        assertEquals("BIDDER", user.getRole());
    }

    @Test @DisplayName("[EP] Factory role không hợp lệ — ném IllegalArgumentException")
    void testFactory_InvalidRole_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> UserFactory.createUser("UNKNOWN", 1, "x"));
    }

    @Test @DisplayName("[EP] Factory role null — ném IllegalArgumentException")
    void testFactory_NullRole_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> UserFactory.createUser(null, 1, "x"));
    }

    @Test @DisplayName("[EP] Factory role rỗng — ném IllegalArgumentException")
    void testFactory_EmptyRole_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> UserFactory.createUser("", 1, "x"));
    }

    /**
     * [2-way] Pairwise testing — kết hợp (role × id) (slide 22-24)
     * Tham số: role = {BIDDER, SELLER, ADMIN}, id = {0, 1, 999}
     *
     * TC | role   | id
     *  1 | BIDDER | 0
     *  2 | BIDDER | 999
     *  3 | SELLER | 1
     *  4 | SELLER | 0
     *  5 | ADMIN  | 999
     *  6 | ADMIN  | 1
     */
    @Test @DisplayName("[2-way] BIDDER + id=0")
    void testFactory_Pairwise_BidderZeroId() {
        var u = UserFactory.createUser("BIDDER", 0, "u1");
        assertEquals("BIDDER", u.getRole());
        assertEquals(0, u.getUserId());
    }

    @Test @DisplayName("[2-way] BIDDER + id=999")
    void testFactory_Pairwise_BidderMaxId() {
        var u = UserFactory.createUser("BIDDER", 999, "u2");
        assertEquals("BIDDER", u.getRole());
        assertEquals(999, u.getUserId());
    }

    @Test @DisplayName("[2-way] SELLER + id=1")
    void testFactory_Pairwise_SellerNominalId() {
        var u = UserFactory.createUser("SELLER", 1, "u3");
        assertEquals("SELLER", u.getRole());
        assertEquals(1, u.getUserId());
    }

    @Test @DisplayName("[2-way] SELLER + id=0")
    void testFactory_Pairwise_SellerZeroId() {
        var u = UserFactory.createUser("SELLER", 0, "u4");
        assertEquals("SELLER", u.getRole());
    }

    @Test @DisplayName("[2-way] ADMIN + id=999")
    void testFactory_Pairwise_AdminMaxId() {
        var u = UserFactory.createUser("ADMIN", 999, "u5");
        assertEquals("ADMIN", u.getRole());
        assertEquals(999, u.getUserId());
    }

    @Test @DisplayName("[2-way] ADMIN + id=1")
    void testFactory_Pairwise_AdminNominalId() {
        var u = UserFactory.createUser("ADMIN", 1, "u6");
        assertEquals("ADMIN", u.getRole());
        assertEquals(1, u.getUserId());
    }
}
