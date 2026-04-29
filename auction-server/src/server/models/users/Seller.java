package server.models.users;

import java.math.BigDecimal;

// 4. INHERITANCE: Seller kế thừa từ User
public class Seller extends User {
    // Thuộc tính riêng của người bán
    private double sellerRating; // Đánh giá sao (vd: 4.8/5.0)
    private int totalItemsSold;  // Số sản phẩm đã bán thành công

    public Seller(int i, String thắng) {
        super();
        this.sellerRating = 5.0;
        this.totalItemsSold = 0;
    }

    public Seller(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        super(userId, username, passwordHash, email, accountBalance);
        this.sellerRating = 5.0;
        this.totalItemsSold = 0;
    }

    // Đa hình (Polymorphism)
    @Override
    public String getRole() {
        return "SELLER";
    }

    public double getSellerRating() { return sellerRating; }
    public void setSellerRating(double sellerRating) { this.sellerRating = sellerRating; }
    public int getTotalItemsSold() { return totalItemsSold; }

    // Nghiệp vụ riêng: Tăng số lượng sản phẩm đã bán khi một phiên đấu giá kết thúc
    public void incrementItemsSold() {
        this.totalItemsSold++;
    }
}