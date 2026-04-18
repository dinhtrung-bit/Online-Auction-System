package server.DAO;

import server.models.Bidder;
import server.models.Seller;
import server.models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserDAOimpl implements UserDAO {

    @Override
    public void insert(User user) throws Exception {
        // Đã sửa tên bảng thành 'users' và đưa PreparedStatement vào trong ngoặc tròn (try-with-resources)
        String sql = "INSERT INTO users(username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(User user) throws Exception {
        String sql = "UPDATE users SET username = ?, password_hash = ?, role = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole());
            pstmt.setString(4, user.getUserId());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public User findByUsername(String username) throws Exception {
        return null;
    }

    @Override
    public List<User> findAll() throws Exception {
        List<User> userList = new ArrayList<>();// 1. Tạo danh sách rỗng để chứa kết quả trả về
        String sql = "SELECT user_id, username, role FROM users";//câu lệnh sql lấy toàn bộ người dùng in ra để mn xem
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                // Lấy dữ liệu từ các cột tương ứng trong bảng 'users'
                String userId = rs.getString("user_id");
                String username = rs.getString("username");
                String role = rs.getString("role");
                User user = null;
                if (role!=null){
                    switch (role.toUpperCase()){
                        case"BIDDER":
                            user=new Bidder( userId, username,0.0);
                            break;
                        case"SELLER":
                            user=new Seller(userId,username);
                            break;
                    }
                }
                if (user != null) {
                    userList.add(user);
                }
            }
        }
        return userList;
    }
}