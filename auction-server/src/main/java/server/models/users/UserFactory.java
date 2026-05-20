package server.models.users;

import java.math.BigDecimal;

/**
 * UserFactory — tạo đúng subclass User theo role string.
 *
 * UserRole enum đã bị xóa vì không được dùng ở bất kỳ đâu ngoài factory này.
 * Factory dùng switch trực tiếp trên string đã upper-case.
 */
public final class UserFactory {

    private UserFactory() {}

    /**
     * Tạo User đầy đủ — dùng khi đã có đủ dữ liệu từ DB hoặc khi tạo mới.
     */
    public static User createUser(String role, int id, String username,
                                  String passwordHash, BigDecimal balance) {
        return switch (normalizeRole(role)) {
            case "BIDDER" -> new Bidder(id, username, passwordHash, balance);
            case "SELLER" -> new Seller(id, username, passwordHash, balance);
            case "ADMIN"  -> new Admin(id, username, passwordHash, balance);
            default -> throw new IllegalArgumentException("Vai trò không hợp lệ: " + role);
        };
    }

    /**
     * Tạo User tối thiểu — dùng nội bộ trong DAO khi map từ ResultSet.
     * Caller phải gọi setPasswordHash() và setAccountBalance() ngay sau.
     */
    public static User createUser(String role, int id, String username) {
        return createUser(role, id, username, "", BigDecimal.ZERO);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Vai trò không được để trống.");
        }
        return role.trim().toUpperCase();
    }
}
