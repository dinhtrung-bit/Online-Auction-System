package server.dao.impl;

import server.dao.core.DBConnection;
import server.models.finance.DepositRequest;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** DAO riêng cho workflow nạp tiền cần Admin duyệt. */
public class DepositRequestDAOImpl {

    public DepositRequestDAOImpl() {
        ensureTable();
    }

    private void ensureTable() {
        String sql = "CREATE TABLE IF NOT EXISTS deposit_requests (" +
                "request_id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "amount DECIMAL(18,2) NOT NULL," +
                "status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                "note VARCHAR(500) NULL," +
                "admin_id INT NULL," +
                "admin_note VARCHAR(500) NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "reviewed_at TIMESTAMP NULL," +
                "INDEX idx_deposit_status(status)," +
                "INDEX idx_deposit_user(user_id)" +
                ")";
        try (Connection conn = DBConnection.getInstance();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            System.err.println(">>> [DB] Không thể tạo bảng deposit_requests: " + e.getMessage());
        }
    }

    public DepositRequest create(int userId, BigDecimal amount, String note) throws Exception {
        ensureTable();
        String sql = "INSERT INTO deposit_requests(user_id, amount, status, note) VALUES (?, ?, 'PENDING', ?)";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setBigDecimal(2, amount);
            ps.setString(3, note);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return findById(rs.getInt(1));
            }
        }
        throw new Exception("Không tạo được yêu cầu nạp tiền.");
    }

    public DepositRequest findById(int requestId) throws Exception {
        ensureTable();
        String sql = baseSelect() + " WHERE dr.request_id = ?";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<DepositRequest> findAll() throws Exception {
        ensureTable();
        String sql = baseSelect() + " ORDER BY dr.created_at DESC";
        return queryList(sql);
    }

    public List<DepositRequest> findPending() throws Exception {
        ensureTable();
        String sql = baseSelect() + " WHERE dr.status = 'PENDING' ORDER BY dr.created_at ASC";
        return queryList(sql);
    }

    public List<DepositRequest> findByUserId(int userId) throws Exception {
        ensureTable();
        String sql = baseSelect() + " WHERE dr.user_id = ? ORDER BY dr.created_at DESC";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DepositRequest> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public boolean markReviewed(Connection conn, int requestId, int adminId, String status, String adminNote) throws Exception {
        String sql = "UPDATE deposit_requests SET status = ?, admin_id = ?, admin_note = ?, reviewed_at = CURRENT_TIMESTAMP " +
                "WHERE request_id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, adminId);
            ps.setString(3, adminNote);
            ps.setInt(4, requestId);
            return ps.executeUpdate() > 0;
        }
    }

    public int countPending() throws Exception {
        ensureTable();
        String sql = "SELECT COUNT(*) FROM deposit_requests WHERE status = 'PENDING'";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public BigDecimal sumByStatus(String status) throws Exception {
        ensureTable();
        String sql = "SELECT COALESCE(SUM(amount),0) FROM deposit_requests WHERE status = ?";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private List<DepositRequest> queryList(String sql) throws Exception {
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<DepositRequest> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            return list;
        }
    }

    private String baseSelect() {
        return "SELECT dr.*, u.username FROM deposit_requests dr " +
                "LEFT JOIN users u ON u.user_id = dr.user_id";
    }

    private DepositRequest map(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp reviewed = rs.getTimestamp("reviewed_at");
        return new DepositRequest(
                rs.getInt("request_id"),
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getBigDecimal("amount"),
                rs.getString("status"),
                rs.getString("note"),
                (Integer) rs.getObject("admin_id"),
                rs.getString("admin_note"),
                created != null ? created.toLocalDateTime() : null,
                reviewed != null ? reviewed.toLocalDateTime() : null
        );
    }
}
