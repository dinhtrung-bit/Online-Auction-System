package server.models.users;

import java.math.BigDecimal;

/**
 * Admin — quản trị viên hệ thống.
 *
 * Admin không thể bid (canBid = false) và không thể sell (canSell = false) —
 * kế thừa default từ User, không cần override.
 *
 * canManageUsers() và canApproveItems() được gọi thực sự trong các handler
 * khi Admin thực hiện thao tác quản trị.
 */
public class Admin extends User {

    private int accessLevel;
    private String department;

    public Admin() {
        super();
        this.accessLevel = 1;
        this.department = "General Management";
    }

    public Admin(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        super(userId, username, passwordHash, email, accountBalance);
        this.accessLevel = 1;
        this.department = "General Management";
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }

    /** Chỉ Super Admin (accessLevel >= 2) mới được quản lý danh sách User. */
    public boolean canManageUsers() {
        return accessLevel >= 2;
    }

    /** Mọi Admin đều có quyền duyệt/hủy phiên đấu giá. */
    public boolean canApproveItems() {
        return true;
    }

    public int getAccessLevel() { return accessLevel; }
    public void setAccessLevel(int accessLevel) { this.accessLevel = accessLevel; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}