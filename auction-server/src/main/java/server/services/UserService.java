package server.services;

import server.dao.core.DBConnection;
import server.dao.impl.DepositRequestDAOImpl;
import server.dao.interfaces.UserDAO;
import server.models.finance.DepositRequest;
import server.models.users.User;
import server.models.users.UserFactory;
import server.utils.Validation;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * UserService — service layer xử lý nghiệp vụ liên quan đến người dùng.
 * Bản V6: nạp tiền chuyển sang workflow REQUEST -> ADMIN APPROVE -> CREDIT.
 */
public class UserService {

    private final UserDAO userDAO;
    private final DepositRequestDAOImpl depositDAO = new DepositRequestDAOImpl();

    /** Constructor Injection — MainServer truyền DAO vào, không tự new. */
    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void register(String username, String password, String role) throws Exception {
        Validation.validateUsername(username);
        Validation.validatePassword(password);

        User user = UserFactory.createUser(role, 0, username);
        user.setPasswordHash(PasswordUtil.hash(password));
        userDAO.insert(user);
    }

    public User login(String username, String password, String role) throws Exception {
        User user = userDAO.findByUsername(username);
        if (user == null) return null;
        if (!PasswordUtil.verify(password, user.getPasswordHash())) return null;
        if (!user.getRole().equalsIgnoreCase(role)) return null;
        return user;
    }

    /**
     * V6: tạo yêu cầu nạp tiền chờ Admin duyệt, KHÔNG cộng tiền ngay.
     */
    public DepositRequest createDepositRequest(User user, BigDecimal amount, String note) throws Exception {
        if (user == null) throw new IllegalArgumentException("Chưa đăng nhập.");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0!");
        }
        if (amount.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new IllegalArgumentException("Số tiền nạp quá lớn. Vui lòng chia thành nhiều yêu cầu nhỏ hơn.");
        }
        return depositDAO.create(user.getUserId(), amount, note);
    }

    /** Giữ lại API cũ cho tương thích nội bộ, nhưng UI không dùng trực tiếp nữa. */
    public BigDecimal deposit(User user, double amount) throws Exception {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0!");
        }
        BigDecimal depositAmount = BigDecimal.valueOf(amount);
        user.updateBalance(depositAmount);
        userDAO.update(user);
        return user.getAccountBalance();
    }

    /** Admin duyệt yêu cầu: cập nhật request + cộng tiền trong cùng transaction. */
    public BigDecimal approveDepositRequest(int requestId, User admin, String adminNote) throws Exception {
        requireAdmin(admin);
        DepositRequest request = depositDAO.findById(requestId);
        if (request == null) throw new IllegalArgumentException("Không tìm thấy yêu cầu nạp tiền.");
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Yêu cầu này đã được xử lý trước đó.");
        }

        try (Connection conn = DBConnection.getInstance()) {
            conn.setAutoCommit(false);
            try {
                boolean marked = depositDAO.markReviewed(conn, requestId, admin.getUserId(), "APPROVED", adminNote);
                if (!marked) throw new IllegalArgumentException("Yêu cầu đã được xử lý bởi admin khác.");

                String sql = "UPDATE users SET balance = COALESCE(balance,0) + ? WHERE user_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setBigDecimal(1, request.getAmount());
                    ps.setInt(2, request.getUserId());
                    if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Không tìm thấy tài khoản nhận tiền.");
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        User fresh = userDAO.findById(request.getUserId());
        return fresh != null ? fresh.getAccountBalance() : BigDecimal.ZERO;
    }

    public void rejectDepositRequest(int requestId, User admin, String adminNote) throws Exception {
        requireAdmin(admin);
        DepositRequest request = depositDAO.findById(requestId);
        if (request == null) throw new IllegalArgumentException("Không tìm thấy yêu cầu nạp tiền.");
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Yêu cầu này đã được xử lý trước đó.");
        }
        try (Connection conn = DBConnection.getInstance()) {
            conn.setAutoCommit(false);
            try {
                boolean marked = depositDAO.markReviewed(conn, requestId, admin.getUserId(), "REJECTED", adminNote);
                if (!marked) throw new IllegalArgumentException("Yêu cầu đã được xử lý bởi admin khác.");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<DepositRequest> findAllDepositRequests() throws Exception {
        return depositDAO.findAll();
    }

    public List<DepositRequest> findPendingDepositRequests() throws Exception {
        return depositDAO.findPending();
    }

    public List<DepositRequest> findDepositRequestsByUser(int userId) throws Exception {
        return depositDAO.findByUserId(userId);
    }

    public int countPendingDeposits() throws Exception {
        return depositDAO.countPending();
    }

    public BigDecimal sumDepositsByStatus(String status) throws Exception {
        return depositDAO.sumByStatus(status);
    }

    /** Admin chỉnh số dư thủ công, có validate và reason để tránh thao tác nhầm. */
    public BigDecimal adminAdjustBalance(int userId, BigDecimal delta, User admin, String reason) throws Exception {
        requireAdmin(admin);
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Số tiền điều chỉnh phải khác 0.");
        }
        User target = userDAO.findById(userId);
        if (target == null) throw new IllegalArgumentException("Không tìm thấy người dùng.");
        BigDecimal newBalance = target.getAccountBalance().add(delta);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Không thể trừ quá số dư hiện có.");
        }
        target.setAccountBalance(newBalance);
        userDAO.update(target);
        System.out.println(">>> [Admin Balance] " + admin.getUsername() + " chỉnh ví user#" + userId +
                " delta=" + delta + " reason=" + reason);
        return newBalance;
    }

    public User findByUsername(String username) throws Exception {
        return userDAO.findByUsername(username);
    }

    public User findById(int id) throws Exception {
        return userDAO.findById(id);
    }

    public java.util.List<server.models.users.User> findAll() throws Exception {
        return userDAO.findAll();
    }

    private void requireAdmin(User user) {
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Không có quyền Admin.");
        }
    }
}
