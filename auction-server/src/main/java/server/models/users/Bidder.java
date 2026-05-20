package server.models.users;

import java.math.BigDecimal;

/**
 * Bidder — người tham gia đặt giá.
 *
 * Chỉ giữ lại canBid() và canPlaceBid() — hai method thực sự được hệ thống gọi.
 * reputationScore đã bị xóa: không lưu DB, không có logic sử dụng.
 */
public class Bidder extends User {

    public Bidder(int i, String alice, String hash, String mail, BigDecimal bigDecimal) {
        super();
    }

    public Bidder(int userId, String username, String passwordHash, BigDecimal accountBalance) {
        super(userId, username, passwordHash, accountBalance);
    }

    @Override public String getRole()   { return "BIDDER"; }
    @Override public boolean canBid()   { return true; }

    /**
     * Kiểm tra số dư có đủ để đặt mức giá này không.
     * Dùng bởi AuctionRoom.placeBid() và AuctionService trước khi gọi placeBid().
     */
    public boolean canPlaceBid(BigDecimal bidAmount) {
        return hasEnoughBalance(bidAmount);
    }
}
