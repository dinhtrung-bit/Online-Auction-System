package client.models.user;

/**
 * UserSession — Singleton đúng nghĩa, thay thế public static fields.
 *
 * Trước: public static String username = "" (global mutable state, không encapsulated)
 * Sau  : private fields + getter/setter + login()/logout() rõ ràng về intent.
 *
 * Lợi ích:
 *   - Encapsulation: không ai gán trực tiếp field từ bên ngoài
 *   - logout() reset tất cả về trạng thái ban đầu trong 1 chỗ
 *   - Dễ mở rộng (thêm userId, email... chỉ sửa 1 class)
 */
public class UserSession {

    private static UserSession instance;

    private String username = "";
    private String role     = "";
    private double balance  = 0.0;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) instance = new UserSession();
        return instance;
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    /** Gọi sau khi đăng nhập thành công. */
    public void login(String username, String role) {
        this.username = username != null ? username : "";
        this.role     = role     != null ? role     : "";
        this.balance  = 0.0;
    }

    /** Gọi khi đăng xuất — reset toàn bộ về trạng thái ban đầu. */
    public void logout() {
        this.username = "";
        this.role     = "";
        this.balance  = 0.0;
    }

    // ── Getters ────────────────────────────────────────────────────

    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public double getBalance()  { return balance; }

    // ── Setters (chỉ cho balance vì được server cập nhật real-time) ─

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void addBalance(double amount) {
        this.balance += amount;
    }

    // ── Convenience ────────────────────────────────────────────────

    public boolean isLoggedIn() {
        return username != null && !username.isEmpty();
    }
}