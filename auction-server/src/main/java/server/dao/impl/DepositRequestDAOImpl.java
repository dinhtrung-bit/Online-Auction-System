package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.core.GenericDAO;
import server.models.finance.DepositRequest;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import server.dao.interfaces.DepositRequestDAO;

/**
 * DAO hoàn thiện: Chống SQL Injection, tối ưu hiệu năng,
 * tương thích GenericDAO và giữ lại các hàm nghiệp vụ cũ.
 */
public class DepositRequestDAOImpl implements GenericDAO<DepositRequest>, DepositRequestDAO {

    // Hằng số truy vấn dùng chung để dễ bảo trì
    private static final String BASE_SELECT = "SELECT dr.*, u.username FROM deposit_requests dr LEFT JOIN users u ON u.user_id = dr.user_id";

    // --- 1. PHƯƠNG THỨC TƯƠNG THÍCH (CHO CODE CŨ) ---

    public DepositRequest create(int userId, BigDecimal amount, String note) throws Exception {
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
        throw new Exception("Lỗi: Không thể tạo yêu cầu nạp tiền mới.");
    }

    public BigDecimal sumByStatus(String status) throws Exception {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM deposit_requests WHERE status = ?";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    // --- 2. TRIỂN KHAI GENERIC DAO ---

    @Override
    public void insert(DepositRequest obj) throws Exception {
        create(obj.getUserId(), obj.getAmount(), obj.getNote());
    }

    @Override
    public void update(DepositRequest obj) throws Exception {
        String sql = "UPDATE deposit_requests SET amount = ?, status = ?, note = ?, admin_id = ?, admin_note = ?, reviewed_at = ? WHERE request_id = ?";
        execute(sql, ps -> {
            ps.setBigDecimal(1, obj.getAmount());
            ps.setString(2, obj.getStatus());
            ps.setString(3, obj.getNote());
            ps.setObject(4, obj.getAdminId());
            ps.setString(5, obj.getAdminNote());
            ps.setObject(6, obj.getReviewedAt() != null ? Timestamp.valueOf(obj.getReviewedAt()) : null);
            ps.setInt(7, obj.getId()); 
        });
    }

    @Override
    public void delete(int id) throws Exception {
        execute("DELETE FROM deposit_requests WHERE request_id = ?", ps -> ps.setInt(1, id));
    }

    @Override
    public DepositRequest findById(int id) throws Exception {
        String sql = BASE_SELECT + " WHERE dr.request_id = ?";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public List<DepositRequest> findAll() throws Exception {
        return queryList(BASE_SELECT + " ORDER BY dr.created_at DESC", ps -> {});
    }

    // --- 3. CÁC HÀM NGHIỆP VỤ THIẾT YẾU ---

    public List<DepositRequest> findPending() throws Exception {
        return queryList(BASE_SELECT + " WHERE dr.status = 'PENDING' ORDER BY dr.created_at ASC", ps -> {});
    }

    public List<DepositRequest> findByUserId(int userId) throws Exception {
        return queryList(BASE_SELECT + " WHERE dr.user_id = ? ORDER BY dr.created_at DESC", ps -> ps.setInt(1, userId));
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
        String sql = "SELECT COUNT(*) FROM deposit_requests WHERE status = 'PENDING'";
        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // --- 4. HÀM HỖ TRỢ (PRIVATE HELPERS) ---

    @FunctionalInterface
    private interface SQLBinder { void bind(PreparedStatement ps) throws SQLException; }

    private void execute(String sql, SQLBinder binder) throws Exception {
        try (Connection conn = DBConnection.getInstance(); PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private List<DepositRequest> queryList(String sql, SQLBinder binder) throws Exception {
        try (Connection conn = DBConnection.getInstance(); PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<DepositRequest> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    private DepositRequest map(ResultSet rs) throws SQLException {
        Timestamp c = rs.getTimestamp("created_at");
        Timestamp r = rs.getTimestamp("reviewed_at");
        return new DepositRequest(
                rs.getInt("request_id"), rs.getInt("user_id"), rs.getString("username"),
                rs.getBigDecimal("amount"), rs.getString("status"), rs.getString("note"),
                (Integer) rs.getObject("admin_id"), rs.getString("admin_note"),
                c != null ? c.toLocalDateTime() : null, r != null ? r.toLocalDateTime() : null
        );
    }
}