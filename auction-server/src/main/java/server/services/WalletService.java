package server.services;

import java.math.BigDecimal;

import server.dao.interfaces.UserDAO;
import server.models.users.User;

/**
 * Chỉ phụ trách nghiệp vụ ví tiền.
 * Logic được chuyển nguyên từ UserService cũ, không đổi validate hoặc kết quả trả về.
 */
public class WalletService {

    private final UserDAO userDAO;

    public WalletService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

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

    private void requireAdmin(User user) {
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Không có quyền Admin.");
        }
    }
}
