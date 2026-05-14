package server.models.users;

import java.math.BigDecimal;

/**
 * Seller — người đăng sản phẩm đấu giá.
 *
 * Fix 3.5: override canSell() = true để handler dùng user.canSell() thay vì instanceof.
 */
public class Seller extends User {

    private double sellerRating;
    private int totalItemsSold;

    public Seller() {
        super();
        this.sellerRating = 5.0;
        this.totalItemsSold = 0;
    }

    public Seller(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        super(userId, username, passwordHash, email, accountBalance);
        this.sellerRating = 5.0;
        this.totalItemsSold = 0;
    }

    @Override
    public String getRole() {
        return "SELLER";
    }

    /** Seller được phép tạo/quản lý phiên đấu giá. */
    @Override
    public boolean canSell() {
        return true;
    }

    public double getSellerRating() { return sellerRating; }
    public void setSellerRating(double sellerRating) { this.sellerRating = sellerRating; }
    public int getTotalItemsSold() { return totalItemsSold; }

    public void incrementItemsSold() {
        this.totalItemsSold++;
    }
}