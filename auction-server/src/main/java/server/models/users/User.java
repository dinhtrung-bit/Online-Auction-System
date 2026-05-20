package server.models.users;

import java.math.BigDecimal;

/**
 * User — lớp trừu tượng gốc của hệ phân cấp người dùng.
 *
 * Nguyên tắc thiết kế:
 *   - Chỉ chứa field thực sự được lưu DB và được đọc trong hệ thống.
 *   - canBid() / canSell() / canAdmin() thay thế instanceof ở handler.
 *   - Nghiệp vụ tài chính (debit/credit) nằm ở đây, không để service tự cộng trừ.
 */
public abstract class User {

    private int userId;
    private String username;
    private String passwordHash;
    private BigDecimal accountBalance;

    protected User() {}

    protected User(int userId, String username, String passwordHash, BigDecimal accountBalance) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.accountBalance = accountBalance != null ? accountBalance : BigDecimal.ZERO;
    }

    // ── Quyền hạn (thay thế instanceof ở handler) ────────────────────────────

    /** Trả true nếu user được phép đặt giá. Override trong Bidder. */
    public boolean canBid()   { return false; }

    /** Trả true nếu user được phép tạo/quản lý sản phẩm. Override trong Seller. */
    public boolean canSell()  { return false; }

    /** Trả true nếu user là admin. Override trong Admin. */
    public boolean canAdmin() { return false; }

    /** Vai trò cụ thể — bắt buộc subclass định nghĩa. */
    public abstract String getRole();

    // ── Nghiệp vụ tài chính ──────────────────────────────────────────────────

    /**
     * Trừ tiền khỏi ví. Trả về false và không thay đổi số dư nếu không đủ tiền.
     * Service layer phải kiểm tra giá trị trả về trước khi persist.
     */
    public boolean debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        BigDecimal after = this.accountBalance.subtract(amount);
        if (after.compareTo(BigDecimal.ZERO) < 0) return false;
        this.accountBalance = after;
        return true;
    }

    /**
     * Cộng tiền vào ví. Luôn thành công với amount hợp lệ.
     */
    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        this.accountBalance = this.accountBalance.add(amount);
    }

    /**
     * Kiểm tra số dư có đủ để chi một khoản tiền không.
     * Dùng trong handler/service để validate trước khi gọi debit().
     */
    public boolean hasEnoughBalance(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        return this.accountBalance.compareTo(amount) >= 0;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getUserId()                { return userId; }
    public String getUsername()           { return username; }
    public String getPasswordHash()       { return passwordHash; }
    public BigDecimal getAccountBalance() { return accountBalance; }

    public void setUserId(int userId)                         { this.userId = userId; }
    public void setUsername(String username)                   { this.username = username; }
    public void setPasswordHash(String passwordHash)           { this.passwordHash = passwordHash; }
    public void setAccountBalance(BigDecimal accountBalance)   {
        this.accountBalance = accountBalance != null ? accountBalance : BigDecimal.ZERO;
    }
}
