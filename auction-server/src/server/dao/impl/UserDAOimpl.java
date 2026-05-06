package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.UserDAO;
import server.models.users.User;
import server.models.users.UserFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
public class UserDAOimpl implements UserDAO {

    @Override
    public void insert(User user) throws Exception {
        // Sử dụng PreparedStatement để tối ưu và đảm bảo bảo mật SQL Injection
        String sql = "INSERT INTO users(username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole());

            pstmt.executeUpdate(); // Thực thi câu lệnh
        }
    }

    @Override
    public void update(User user) throws Exception {
        String sql = "UPDATE users SET username = ?, password_hash = ?, role = ?, balance = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole());
            pstmt.setBigDecimal(4, user.getAccountBalance());
            pstmt.setInt(5, user.getUserId());

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
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int userId = rs.getInt("user_id");
                    String uname = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    String role = rs.getString("role");

                    // THÊM DÒNG NÀY: Lấy số dư từ cột 'balance' trong MySQL
                    BigDecimal balance = rs.getBigDecimal("balance");
                    if (balance == null) balance = BigDecimal.ZERO;

                    User user = UserFactory.createUser(role, userId, uname);
                    if (user != null) {
                        user.setPasswordHash(passwordHash);
                        user.setAccountBalance(balance); // Nạp tiền vào bộ nhớ
                    }
                    return user;
                }
            }
        }
        return null;
    }

    @Override
    public List<User> findAll() throws Exception {
        List<User> userList = new ArrayList<>();
        String sql = "SELECT user_id, username, password_hash, role FROM users";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                int userId = rs.getInt("user_id");
                String username = rs.getString("username");
                String passwordHash = rs.getString("password_hash");
                String role = rs.getString("role");

                User user = UserFactory.createUser(role, userId, username);
                if (user != null) {
                    user.setPasswordHash(passwordHash); // Nạp pass để tránh lỗi hiển thị/xử lý
                    userList.add(user);
                }
            }
        }
        return userList;
    }
    @Override
    public boolean transferMoney(int fromUserId, int toUserId, BigDecimal amount) {
        String sqlWithdraw = "UPDATE Users SET accountBalance = accountBalance - ? WHERE id = ? AND accountBalance >= ?";
        String sqlDeposit = "UPDATE Users SET accountBalance = accountBalance + ? WHERE id = ?";

        // Lưu ý: Thay DBConnection.getConnection() bằng object lấy connection của dự án bạn
        try (java.sql.Connection conn = server.dao.core.DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false); // Bắt đầu Transaction

            try (java.sql.PreparedStatement pstmtWithdraw = conn.prepareStatement(sqlWithdraw);
                 java.sql.PreparedStatement pstmtDeposit = conn.prepareStatement(sqlDeposit)) {

                // Trừ tiền người mua
                pstmtWithdraw.setBigDecimal(1, amount);
                pstmtWithdraw.setInt(2, fromUserId);
                pstmtWithdraw.setBigDecimal(3, amount); // Đảm bảo số dư >= giá mua
                int rowsAffectedWithdraw = pstmtWithdraw.executeUpdate();

                // Nếu người mua không đủ tiền (rowsAffected = 0) thì Rollback
                if (rowsAffectedWithdraw == 0) {
                    conn.rollback();
                    return false;
                }

                // Cộng tiền cho người bán
                pstmtDeposit.setBigDecimal(1, amount);
                pstmtDeposit.setInt(2, toUserId);
                pstmtDeposit.executeUpdate();

                // Nếu cả 2 lệnh trên OK -> Commit
                conn.commit();
                return true;

            } catch (Exception ex) {
                conn.rollback(); // Có lỗi bất ngờ thì hoàn tác toàn bộ
                ex.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public User findById(int id) throws Exception {
        String sql = "SELECT * FROM users WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        }
        return null;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        int userId = rs.getInt("user_id");
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");


        BigDecimal balance = rs.getBigDecimal("balance");
        if (balance == null) balance = BigDecimal.ZERO;

        User user = UserFactory.createUser(role, userId, username);
        if (user != null) {
            user.setPasswordHash(passwordHash);
            user.setAccountBalance(balance); // Nạp tiền vào bộ nhớ
        }
        return user;
    }
}