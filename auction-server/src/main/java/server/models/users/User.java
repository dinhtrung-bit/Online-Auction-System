package server.models.users;

import java.math.BigDecimal;

/**
 * User — lớp trừu tượng gốc của hệ phân cấp người dùng.
 *
 * Fix 3.4 (Encapsulation):
 *   Đổi tất cả field từ protected → private.
 *   Subclass truy cập qua getter/setter thay vì trực tiếp (Bidder.canPlaceBid dùng getAccountBalance()).
 *
 * Fix 3.5 (Polymorphism — thay instanceof):
 *   Thêm canBid() và canSell() để handler gọi user.canBid() thay vì instanceof Bidder.
 *   Default trả false; Bidder và Seller override thành true.
 */
public abstract class User {

    private int userId;
    private String username;
    private String passwordHash;
    private String email;
    private BigDecimal accountBalance;

    public User() {}

    public User(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.accountBalance = accountBalance;
    }

    // ── Quyền hạn — dùng để thay instanceof trong handler (Fix 3.5) ──────────

    /** Bidder trả về true; Seller và Admin trả về false. */
    public boolean canBid() {
        return false;
    }

    /** Seller trả về true; Bidder và Admin trả về false. */
    public boolean canSell() {
        return false;
    }

    // ── Nghiệp vụ ────────────────────────────────────────────────────────────

    /**
     * Cộng/trừ số dư tài khoản.
     * Trả về true nếu thành công, false nếu số dư sau khi trừ âm (giao dịch bị từ chối).
     * Caller phải kiểm tra giá trị trả về — không được bỏ qua.
     */
    public boolean updateBalance(BigDecimal amount) {
        BigDecimal newBalance = this.accountBalance.add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) >= 0) {
            this.accountBalance = newBalance;
            return true;
        }
        return false;
    }

    /** Vai trò cụ thể — bắt buộc subclass tự định nghĩa (Polymorphism). */
    public abstract String getRole();

    // ── Getters ──────────────────────────────────────────────────────────────

    public int getUserId()            { return userId; }
    public String getUsername()       { return username; }
    public String getPasswordHash()   { return passwordHash; }
    public String getEmail()          { return email; }
    public BigDecimal getAccountBalance() { return accountBalance; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setUserId(int userId)               { this.userId = userId; }
    public void setUsername(String username)         { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setEmail(String email)               { this.email = email; }
    public void setAccountBalance(BigDecimal accountBalance) { this.accountBalance = accountBalance; }
}