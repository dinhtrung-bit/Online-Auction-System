package server.models.users;

import java.math.BigDecimal;

/**
 * Bidder — người tham gia đặt giá.
 *
 * Fix 3.4: dùng getAccountBalance() thay vì truy cập field protected trực tiếp.
 * Fix 3.5: override canBid() = true để handler dùng user.canBid() thay vì instanceof.
 */
public class Bidder extends User {

    private int reputationScore;

    public Bidder() {
        super();
        this.reputationScore = 100;
    }

    public Bidder(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        super(userId, username, passwordHash, email, accountBalance);
        this.reputationScore = 100;
    }

    @Override
    public String getRole() {
        return "BIDDER";
    }

    /** Bidder được phép đặt giá. */
    @Override
    public boolean canBid() {
        return true;
    }

    /** Kiểm tra số dư đủ để đặt mức giá cho trước. */
    public boolean canPlaceBid(BigDecimal bidAmount) {
        return getAccountBalance().compareTo(bidAmount) >= 0;
    }

    public int getReputationScore() {
        return reputationScore;
    }

    public void setReputationScore(int reputationScore) {
        this.reputationScore = reputationScore;
    }
}