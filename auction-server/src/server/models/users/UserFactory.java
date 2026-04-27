package server.models.users;

/**
 * UserFactory: Áp dụng Factory Design Pattern để quản lý việc khởi tạo người dùng.
 * Giúp đáp ứng tiêu chí "Áp dụng design pattern phù hợp" của bài tập lớn.
 */
public class UserFactory {

    /**
     * Phương thức tĩnh để tạo đối tượng User dựa trên vai trò.
     * * @param role: "BIDDER", "SELLER", hoặc "ADMIN"
     * @param id: ID định danh từ Database
     * @param username: Tên đăng nhập của người dùng
     * @return Một đối tượng kế thừa từ lớp User (Bidder, Seller hoặc Admin)
     */
    public static User createUser(String role, int id, String username) {
        if (role == null) {
            throw new IllegalArgumentException("Vai trò không được để trống (null)");
        }

        // Chuyển role về chữ hoa để tránh lỗi so sánh (ví dụ "bidder" vs "BIDDER")
        String userRole = role.toUpperCase();

        switch (userRole) {
            case "BIDDER":
                // Trả về đối tượng Bidder với các giá trị mặc định ban đầu
                // passwordHash, email được để trống để cập nhật sau qua Setter hoặc DAO
                return new Bidder(id, username, "", "", 0.0);

            case "SELLER":
                // Trả về đối tượng Seller cho người bán hàng
                return new Seller(id, username, "", "", 0.0);

            case "ADMIN":
                // Khởi tạo Admin (lớp Admin kế thừa User đã có trong source của bạn)
                return new Admin(id, username, "", "", 0.0);

            default:
                // Xử lý lỗi nếu Role gửi lên từ Client không nằm trong danh sách hỗ trợ
                throw new IllegalArgumentException("Loại người dùng không hợp lệ: " + role);
        }
    }
}