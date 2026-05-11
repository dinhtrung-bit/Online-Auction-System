package server.models.users;

import java.math.BigDecimal;

/**
 * UserFactory: Áp dụng Factory Design Pattern để quản lý việc khởi tạo người dùng.
 * Sử dụng Enum UserRole để kiểm soát chặt chẽ các loại tài khoản.
 */
public class UserFactory {

    public static User createUser(String role, int id, String username) {
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Vai trò không được để trống (null)");
        }

        UserRole userRole;
        try {
            // Chuyển đổi String gửi từ Client thành Enum chuẩn
            userRole = UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Loại người dùng không hợp lệ: " + role);
        }

        // Switch trực tiếp trên Enum
        switch (userRole) {
            case BIDDER:
                return new Bidder(id, username, "", "", BigDecimal.valueOf(0));

            case SELLER:
                return new Seller(id, username, "", "", BigDecimal.valueOf(0));

            case ADMIN:
                return new Admin(id, username, "", "", BigDecimal.valueOf(0));

            default:
                throw new IllegalArgumentException("Vai trò chưa được hỗ trợ khởi tạo: " + userRole);
        }
    }
}