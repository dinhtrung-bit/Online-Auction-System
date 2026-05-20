package server.models.users;

import java.math.BigDecimal;

/**
 * Seller — người đăng sản phẩm đấu giá.
 *
 * Fix 3.5: override canSell() = true để handler dùng user.canSell() thay vì instanceof.
 */
public class Seller extends User {

    private int totalItemsSold;

    public Seller() {
        super();
        this.totalItemsSold = 0;
    }

    public Seller(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        super(userId, username, passwordHash, email, accountBalance);
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

    public int getTotalItemsSold() { return totalItemsSold; }

    public void incrementItemsSold() {
        this.totalItemsSold++;
    }
}