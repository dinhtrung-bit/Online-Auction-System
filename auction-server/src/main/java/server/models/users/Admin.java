package server.models.users;

import java.math.BigDecimal;

/**
 * Admin — quản trị viên hệ thống.
 *
 * accessLevel và department đã bị xóa: không được lưu/đọc từ DB.
 * canManageUsers() và canApproveItems() đã bị xóa: không được gọi ở bất kỳ đâu.
 *
 * canAdmin() là method duy nhất phân biệt Admin, thay thế việc so sánh chuỗi
 * "ADMIN".equalsIgnoreCase(user.getRole()) trong các handler.
 */
public class Admin extends User {

    public Admin() {
        super();
    }

    public Admin(int userId, String username, String passwordHash, BigDecimal accountBalance) {
        super(userId, username, passwordHash, accountBalance);
    }

    @Override public String getRole()    { return "ADMIN"; }
    @Override public boolean canAdmin()  { return true; }
}
