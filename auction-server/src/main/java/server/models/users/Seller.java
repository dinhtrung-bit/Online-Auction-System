package server.models.users;

import java.math.BigDecimal;

/**
 * Seller — người đăng sản phẩm và tạo phiên đấu giá.
 *
 * sellerRating và totalItemsSold đã bị xóa: không được lưu DB, không có logic sử dụng.
 * canSell() là method duy nhất phân biệt Seller với các role khác.
 */
public class Seller extends User {

    public Seller(int i, String bob, String hash, String mail, BigDecimal bigDecimal) {
        super();
    }

    public Seller(int userId, String username, String passwordHash, BigDecimal accountBalance) {
        super(userId, username, passwordHash, accountBalance);
    }

    @Override public String getRole()   { return "SELLER"; }
    @Override public boolean canSell()  { return true; }
}
