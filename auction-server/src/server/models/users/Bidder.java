package server.models.users;

// 4. INHERITANCE: Bidder kế thừa tất cả thuộc tính/hàm từ User
public class Bidder extends User {
    // Thuộc tính riêng của người mua
    private int reputationScore; // Điểm uy tín (tránh spam bid)

    public Bidder() {
        super();
        this.reputationScore = 100; // Mặc định khi tạo mới
    }

    public Bidder(int userId, String username, String passwordHash, String email, double accountBalance) {
        // Gọi Constructor của lớp cha (User)
        super(userId, username, passwordHash, email, accountBalance);
        this.reputationScore = 100;
    }

    // Đa hình (Polymorphism): Ghi đè phương thức của lớp cha
    @Override
    public String getRole() {
        return "BIDDER";
    }

    public int getReputationScore() { return reputationScore; }
    public void setReputationScore(int reputationScore) { this.reputationScore = reputationScore; }

    // Nghiệp vụ riêng: Kiểm tra xem người này có đủ tiền để đặt mức giá đó không
    public boolean canPlaceBid(double bidAmount) {
        return this.accountBalance >= bidAmount;
    }
}