

import org.junit.jupiter.api.Test;
import server.models.users.Bidder;
import server.models.users.Seller;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    @Test
    public void testUpdateBalanceSuccess() {
        Bidder bidder = new Bidder(1, "test_user", "hash", "email@test.com", new BigDecimal("1000"));

        // Nạp thêm tiền
        assertTrue(bidder.updateBalance(new BigDecimal("500")));
        assertEquals(new BigDecimal("1500"), bidder.getAccountBalance());

        // Trừ tiền hợp lệ (Mô phỏng thanh toán khi phiên đấu giá PAID)
        assertTrue(bidder.updateBalance(new BigDecimal("-1000")));
        assertEquals(new BigDecimal("500"), bidder.getAccountBalance());
    }

    @Test
    public void testUpdateBalanceInsufficientFunds() {
        Bidder bidder = new Bidder(1, "test_user", "hash", "email@test.com", new BigDecimal("1000"));

        // Trừ quá số dư -> Trả về false và giữ nguyên số tiền
        assertFalse(bidder.updateBalance(new BigDecimal("-1500")));
        assertEquals(new BigDecimal("1000"), bidder.getAccountBalance());
    }

    @Test
    public void testSellerReceiveFunds() {
        Seller seller = new Seller(99, "seller_1", "hash", "seller@test.com", new BigDecimal("0"));

        // Người bán nhận được tiền khi phiên đấu giá kết thúc thành công
        assertTrue(seller.updateBalance(new BigDecimal("50000")));
        assertEquals(new BigDecimal("50000"), seller.getAccountBalance());
    }
}