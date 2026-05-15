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
 * [EP]  = Equivalence Partitioning
 * [BVA] = Boundary Value Analysis
 * [EG]  = Error Guessing
 * [2-way] = Pairwise / 2-way Combinatorial Testing (slide 20-24)
 */
public class UserTest {

    // @BeforeEach: chuẩn bị object trước mỗi test (slide 38)
    private Bidder bidder;
    private Seller seller;

    @BeforeEach
    void setUp() {
        bidder = new Bidder(1, "alice", "hash", "a@x.com", new BigDecimal("1000"));
        seller = new Seller(2, "bob",   "hash", "b@x.com", new BigDecimal("0"));
    }

    // ================================================================
    // NHÓM 1: updateBalance — nạp/trừ tiền
    // EP: amount dương / amount âm vừa đủ / amount âm quá mức / amount = 0
    // BVA: ngưỡng 0 của số dư sau khi cộng
    // ================================================================

    @Test @DisplayName("[EP] Nạp tiền dương — số dư tăng đúng")
    void testUpdateBalance_Deposit_EP() {
        // Đại diện lớp: amount > 0
        assertTrue(bidder.updateBalance(new BigDecimal("500")));
        assertEquals(new BigDecimal("1500"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EP] Trừ tiền vừa đủ — hợp lệ")
    void testUpdateBalance_WithdrawValid_EP() {
        // Đại diện lớp: amount âm nhưng balance sau >= 0
        assertTrue(bidder.updateBalance(new BigDecimal("-800")));
        assertEquals(new BigDecimal("200"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EP] Trừ quá số dư — không hợp lệ, balance giữ nguyên")
    void testUpdateBalance_WithdrawTooMuch_EP() {
        // Đại diện lớp: balance sau < 0 → phải trả false
        assertFalse(bidder.updateBalance(new BigDecimal("-1500")));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[BVA] Trừ đúng bằng số dư — boundary (balance sau = 0)")
    void testUpdateBalance_WithdrawExact_BVA() {
        // 1000 - 1000 = 0 → balance == 0 → hợp lệ (compareTo(ZERO) >= 0)
        assertTrue(bidder.updateBalance(new BigDecimal("-1000")));
        assertEquals(BigDecimal.ZERO.setScale(0), bidder.getAccountBalance().setScale(0));
    }

    @Test @DisplayName("[BVA] Trừ hơn số dư 0.01 — boundary min- (không hợp lệ)")
    void testUpdateBalance_OnePennyOver_BVA() {
        // 1000 - 1000.01 = -0.01 < 0 → không hợp lệ
        assertFalse(bidder.updateBalance(new BigDecimal("-1000.01")));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[BVA] Nạp amount = 0 — boundary zero (hợp lệ, balance không đổi)")
    void testUpdateBalance_ZeroAmount_BVA() {
        // amount = 0 → balance không đổi, vẫn trả true (0 >= 0)
        assertTrue(bidder.updateBalance(BigDecimal.ZERO));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EG] Nạp tiền nhiều lần liên tiếp — cộng dồn đúng")
    void testUpdateBalance_MultipleDeposits_EG() {
        // [EG] Lỗi cộng dồn hay xảy ra khi dùng primitive thay vì BigDecimal
        bidder.updateBalance(new BigDecimal("100"));
        bidder.updateBalance(new BigDecimal("200"));
        bidder.updateBalance(new BigDecimal("300"));
        assertEquals(new BigDecimal("1600"), bidder.getAccountBalance());
    }

    @Test @DisplayName("[EG] Seller nhận tiền từ số dư 0 — nghiệp vụ kết thúc auction")
    void testSeller_ReceiveFromZero_EG() {
        assertTrue(seller.updateBalance(new BigDecimal("50000")));
        assertEquals(new BigDecimal("50000"), seller.getAccountBalance());
    }

    // ================================================================
    // NHÓM 2: canPlaceBid (Bidder)
    // EP: balance >= amount / balance < amount
    // BVA: balance == amount (đúng bằng), balance = amount - 0.01
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
        // 1000 == 1000 → compareTo = 0 → true
        assertTrue(bidder.canPlaceBid(new BigDecimal("1000")));
    }

    @Test @DisplayName("[BVA] canPlaceBid — hơn balance 0.01")
    void testCanPlaceBid_OnePennyOver_BVA() {
        assertFalse(bidder.canPlaceBid(new BigDecimal("1000.01")));
    }

    // ================================================================
    // NHÓM 3: getRole() — Polymorphism
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
        Admin admin = new Admin(3, "admin", "hash", "admin@x.com", BigDecimal.ZERO);
        assertEquals("ADMIN", admin.getRole());
    }

    // ================================================================
    // NHÓM 4: UserFactory — Factory Method Pattern
    // EP: role hợp lệ {BIDDER, SELLER, ADMIN} / role không hợp lệ / null
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
        // Đại diện lớp: role đúng nhưng viết thường
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
     * 2-way đảm bảo mỗi cặp (role, id) xuất hiện ít nhất 1 lần.
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