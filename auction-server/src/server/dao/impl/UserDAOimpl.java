package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.UserDAO;
import server.models.users.User;
import server.models.users.UserFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserDAOimpl implements UserDAO {

    @Override
    public void insert(User user) throws Exception {
        // và đưa PreparedStatement biên dịch trước để tôis ưu và đảm bảo bảo mật cho câu lệnh
        String sql = "INSERT INTO users(username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole());

            pstmt.executeUpdate();//thực thi câu lệnh sql trả về 1 int tượng trưng cho số hàng được tác động
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
            pstmt.setInt(4, user.getUserId());

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
             ResultSet rs = pstmt.executeQuery()) {//trả về String thực thi truy vấn
            while (rs.next()) {
                // Lấy dữ liệu từ các cột tương ứng trong bảng 'users'
                int userId = rs.getInt("user_id");
                String username = rs.getString("username");
                String role = rs.getString("role");
                User user = UserFactory.createUser(role, userId, username);
                if (user != null) {
                    userList.add(user);
                }
            }
        }
        return userList;
    }
}