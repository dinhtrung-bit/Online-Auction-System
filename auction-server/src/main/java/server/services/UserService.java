package server.services;

import java.math.BigDecimal;
import java.util.List;

import server.dao.interfaces.UserDAO;
import server.models.users.User;
import server.models.users.UserFactory;
import server.utils.Validation;

/** Service layer xử lý nghiệp vụ liên quan đến người dùng. */
public class UserService {

    private final UserDAO userDAO;

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
        if (user == null) {
            return null;
        }
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            return null;
        }
        if (!user.getRole().equalsIgnoreCase(role)) {
            return null;
        }
        return user;
    }

    /** Bidder tự nạp tiền vào ví — cộng tiền ngay, không cần Admin duyệt. */
    public BigDecimal selfDeposit(User user, BigDecimal amount) throws Exception {
        if (user == null) {
            throw new IllegalArgumentException("Chưa đăng nhập.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
        }
        if (amount.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new IllegalArgumentException("Số tiền nạp tối đa mỗi lần là 1.000.000.000 đ.");
        }

        User fresh = userDAO.findById(user.getUserId());
        if (fresh == null) {
            throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        }

        BigDecimal newBalance = fresh.getAccountBalance().add(amount);
        fresh.setAccountBalance(newBalance);
        userDAO.update(fresh);

        System.out.println(">>> [SelfDeposit] user#" + user.getUserId()
                + " nạp " + amount + " → số dư mới " + newBalance);
        return newBalance;
    }

    /** Admin chỉnh số dư thủ công, có validate và reason để tránh thao tác nhầm. */
    public BigDecimal adminAdjustBalance(int userId, BigDecimal delta, User admin, String reason)
            throws Exception {
        requireAdmin(admin);
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Số tiền điều chỉnh phải khác 0.");
        }

        User target = userDAO.findById(userId);
        if (target == null) {
            throw new IllegalArgumentException("Không tìm thấy người dùng.");
        }

        BigDecimal newBalance = target.getAccountBalance().add(delta);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Không thể trừ quá số dư hiện có.");
        }

        target.setAccountBalance(newBalance);
        userDAO.update(target);

        System.out.println(
                ">>> [Admin Balance] " + admin.getUsername()
                        + " chỉnh ví user#" + userId + " delta=" + delta + " reason=" + reason);
        return newBalance;
    }

    public User findByUsername(String username) throws Exception {
        return userDAO.findByUsername(username);
    }

    public User findById(int id) throws Exception {
        return userDAO.findById(id);
    }

    public List<User> findAll() throws Exception {
        return userDAO.findAll();
    }

    private void requireAdmin(User user) {
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Không có quyền Admin.");
        }
    }
}