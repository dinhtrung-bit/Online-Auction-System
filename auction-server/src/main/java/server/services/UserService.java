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
    private final WalletService walletService;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.walletService = new WalletService(userDAO);
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

    /** Giữ nguyên public method cũ, chỉ chuyển xử lý sang WalletService. */
    public BigDecimal selfDeposit(User user, BigDecimal amount) throws Exception {
        return walletService.selfDeposit(user, amount);
    }

    /** Giữ nguyên public method cũ, chỉ chuyển xử lý sang WalletService. */
    public BigDecimal adminAdjustBalance(int userId, BigDecimal delta, User admin, String reason)
            throws Exception {
        return walletService.adminAdjustBalance(userId, delta, admin, reason);
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
}
