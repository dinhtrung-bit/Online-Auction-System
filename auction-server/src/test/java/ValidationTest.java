

import org.junit.jupiter.api.Test;
import server.exceptions.InvalidBidException;
import server.exceptions.Validation;
import server.models.auction.AuctionStatus;
import server.models.users.Bidder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationTest {

    @Test
    public void testValidateEmail() {
        assertDoesNotThrow(() -> Validation.validateEmail("test@example.com"));
        assertThrows(IllegalArgumentException.class, () -> Validation.validateEmail("invalid-email"));
        assertThrows(IllegalArgumentException.class, () -> Validation.validateEmail(""));
    }

    @Test
    public void testValidateBidAmount() {
        assertDoesNotThrow(() -> Validation.validateBidAmount("1500.50"));
        assertThrows(InvalidBidException.class, () -> Validation.validateBidAmount("-500"));
        assertThrows(InvalidBidException.class, () -> Validation.validateBidAmount("abc"));
    }

    @Test
    public void testValidatePaymentAbility() {
        Bidder bidder = new Bidder(1, "Alice", "pw", "alice@mail.com", new BigDecimal("1000"));

        // Đủ tiền -> Không ném ra lỗi
        assertDoesNotThrow(() -> Validation.validatePaymentAbility(bidder, new BigDecimal("500")));

        // Không đủ tiền -> Ném ra Exception
        Exception exception = assertThrows(Exception.class, () -> Validation.validatePaymentAbility(bidder, new BigDecimal("1500")));
        assertTrue(exception.getMessage().contains("Số dư tài khoản hiện tại không đủ"));
    }

    @Test
    public void testCanTransitionTo() {
        // Hợp lệ
        assertTrue(Validation.canTransitionTo(AuctionStatus.OPEN, AuctionStatus.RUNNING));
        assertTrue(Validation.canTransitionTo(AuctionStatus.FINISHED, AuctionStatus.PAID));
        assertTrue(Validation.canTransitionTo(AuctionStatus.FINISHED, AuctionStatus.CANCELED));

        // Không hợp lệ (nhảy cóc trạng thái)
        assertFalse(Validation.canTransitionTo(AuctionStatus.RUNNING, AuctionStatus.PAID));
        assertFalse(Validation.canTransitionTo(AuctionStatus.OPEN, AuctionStatus.PAID));
    }
}