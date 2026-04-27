package server.models.users;

// 1. ABSTRACTION: Lớp trừu tượng, không thể dùng lệnh 'new User()'
public abstract class User {
    // 2. ENCAPSULATION: Dùng 'protected' để bảo vệ dữ liệu nhưng cho phép lớp con truy cập
    protected int userId;
    protected String username;
    protected String passwordHash;
    protected String email;
    protected double accountBalance;

    public User() {}

    public User(int userId, String username, String passwordHash, String email, double accountBalance) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.accountBalance = accountBalance;
    }

    // Getters
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmail() { return email; }
    public double getAccountBalance() { return accountBalance; }

    // Setters
    public void setUserId(int userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setEmail(String email) { this.email = email; }

    // Phương thức chung để nạp/trừ tiền (Ví dụ: khi đặt giá hoặc nạp tiền)
    public boolean updateBalance(double amount) {
        if (this.accountBalance + amount >= 0) {
            this.accountBalance += amount;
            return true;
        }
        return false;
    }

    // 3. POLYMORPHISM: Phương thức trừu tượng, bắt buộc các lớp con phải tự định nghĩa
    public abstract String getRole();
}