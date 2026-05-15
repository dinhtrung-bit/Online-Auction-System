package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.UserDAO;
import server.models.users.User;
import server.models.users.UserFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAOImpl implements UserDAO {

    @Override
    public void insert(User user) throws Exception {
        if (user == null) {
            throw new IllegalArgumentException("User không được null.");
        }

        String sql = "INSERT INTO users(username, password_hash, role, balance) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole());
            pstmt.setBigDecimal(4,
                    user.getAccountBalance() != null ? user.getAccountBalance() : BigDecimal.ZERO);

            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(User user) throws Exception {
        if (user == null) {
            throw new IllegalArgumentException("User không được null.");
        }

        String sql = "UPDATE users SET username = ?, password_hash = ?, role = ?, balance = ? WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole());
            pstmt.setBigDecimal(4,
                    user.getAccountBalance() != null ? user.getAccountBalance() : BigDecimal.ZERO);
            pstmt.setInt(5, user.getUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy user để cập nhật.");
            }
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM users WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy user để xóa.");
            }
        }
    }

    @Override
    public User findByUsername(String username) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT user_id, username, password_hash, role, balance FROM users WHERE username = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username.trim());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        }

        return null;
    }

    @Override
    public User findById(int id) throws Exception {
        String sql = "SELECT user_id, username, password_hash, role, balance FROM users WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance();
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

    @Override
    public List<User> findAll() throws Exception {
        List<User> userList = new ArrayList<>();
        String sql = "SELECT user_id, username, password_hash, role, balance FROM users ORDER BY user_id ASC";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                User user = mapResultSetToUser(rs);
                if (user != null) {
                    userList.add(user);
                }
            }
        }

        return userList;
    }

    @Override
    public boolean transferMoney(int fromUserId, int toUserId, BigDecimal amount) {
        if (fromUserId == toUserId) {
            return false;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        String sqlWithdraw =
                "UPDATE users SET balance = balance - ? " +
                        "WHERE user_id = ? AND balance >= ?";

        String sqlDeposit =
                "UPDATE users SET balance = COALESCE(balance, 0) + ? " +
                        "WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance()) {
            conn.setAutoCommit(false);

            try (PreparedStatement withdrawStmt = conn.prepareStatement(sqlWithdraw);
                 PreparedStatement depositStmt = conn.prepareStatement(sqlDeposit)) {

                withdrawStmt.setBigDecimal(1, amount);
                withdrawStmt.setInt(2, fromUserId);
                withdrawStmt.setBigDecimal(3, amount);

                int withdrawnRows = withdrawStmt.executeUpdate();
                if (withdrawnRows == 0) {
                    conn.rollback();
                    return false;
                }

                depositStmt.setBigDecimal(1, amount);
                depositStmt.setInt(2, toUserId);

                int depositedRows = depositStmt.executeUpdate();
                if (depositedRows == 0) {
                    conn.rollback();
                    return false;
                }

                conn.commit();
                return true;

            } catch (Exception e) {
                conn.rollback();
                System.err.println(">>> [UserDAO.transferMoney] " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            System.err.println(">>> [UserDAO.transferMoney] " + e.getMessage());
            return false;
        }
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        int userId = rs.getInt("user_id");
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        String role = rs.getString("role");

        BigDecimal balance = rs.getBigDecimal("balance");
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }

        User user = UserFactory.createUser(role, userId, username);

        if (user != null) {
            user.setPasswordHash(passwordHash);
            user.setAccountBalance(balance);
        }

        return user;
    }
}