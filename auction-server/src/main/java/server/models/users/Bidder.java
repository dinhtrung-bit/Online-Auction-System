package server.models.users;

import java.math.BigDecimal;

/*
 * Bidder — người tham gia đặt giá.
 */
public class Bidder extends User {



    public Bidder() {
        super();
    }

    public Bidder(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        super(userId, username, passwordHash, email, accountBalance);

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


    }
