package server.models.users;

import java.math.BigDecimal;

/**
 * Admin: Lớp dành cho người quản trị hệ thống.
 * Kế thừa từ User và bổ sung các quyền hạn đặc biệt.
 */
public class Admin extends User {
    // Thuộc tính riêng: Cấp độ truy cập (ví dụ: 1 - Moderator, 2 - Super Admin)
    private int accessLevel;
    private String department;

    public Admin() {
        super();
        this.accessLevel = 1;
        this.department = "General Management";
    }

    public Admin(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        // Gọi Constructor của lớp cha (User)
        super(userId, username, passwordHash, email, accountBalance);
        this.accessLevel = 1;
        this.department = "General Management";
    }

    // Đa hình (Polymorphism): Trả về vai trò cụ thể là ADMIN
    @Override
    public String getRole() {
        return "ADMIN";
    }

    // Các Getter và Setter cho thuộc tính riêng
    public int getAccessLevel() { return accessLevel; }
    public void setAccessLevel(int accessLevel) { this.accessLevel = accessLevel; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    // Nghiệp vụ riêng: Kiểm tra quyền thực hiện các thao tác nhạy cảm
    public boolean canManageUsers() {
        return accessLevel >= 2; // Chỉ Admin cấp cao mới có quyền quản lý User
    }

    public boolean canApproveItems() {
        return true; // Mọi Admin đều có quyền duyệt sản phẩm
    }
}