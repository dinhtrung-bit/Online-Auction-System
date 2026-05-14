package server.models.users;

import java.math.BigDecimal;

/**
 * UserFactory — tạo đúng subclass User theo role.
 *
 * Fix 3.6 (object construction in stages):
 *   Cung cấp 2 overload:
 *   - createUser(role, id, username, passwordHash, email, balance): tạo object hợp lệ hoàn toàn.
 *   - createUser(role, id, username): giữ lại để tương thích với UserDAOImpl.mapResultSetToUser()
 *     — DAO sẽ gọi setPasswordHash/setAccountBalance ngay sau đó.
 */
public class UserFactory {

    private UserFactory() {
        // utility class
    }

    /**
     * Tạo User đầy đủ — object hợp lệ ngay sau construction.
     * Dùng khi tạo user mới (register) hoặc khi đã có đủ thông tin.
     */
    public static User createUser(String role, int id, String username,
                                  String passwordHash, String email, BigDecimal balance) {
        User user = createUser(role, id, username);
        user.setPasswordHash(passwordHash);
        user.setEmail(email);
        user.setAccountBalance(balance);
        return user;
    }

    /**
     * Tạo User với thông tin tối thiểu — dùng nội bộ trong DAO khi map từ ResultSet.
     * Caller phải gọi setPasswordHash(), setAccountBalance() ngay sau.
     */
    public static User createUser(String role, int id, String username) {
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Vai trò không được để trống (null)");
        }

        UserRole userRole;
        try {
            userRole = UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Loại người dùng không hợp lệ: " + role);
        }

        switch (userRole) {
            case BIDDER:
                return new Bidder(id, username, "", "", BigDecimal.ZERO);
            case SELLER:
                return new Seller(id, username, "", "", BigDecimal.ZERO);
            case ADMIN:
                return new Admin(id, username, "", "", BigDecimal.ZERO);
            default:
                throw new IllegalArgumentException("Vai trò chưa được hỗ trợ: " + userRole);
        }
    }
}