package server.services;

import server.dao.interfaces.UserDAO;
import server.models.users.User;
import server.models.users.UserFactory;
import server.utils.Validation;

import java.math.BigDecimal;

/**
 * UserService — service layer xử lý nghiệp vụ liên quan đến người dùng.
 * ClientHandler chỉ parse request rồi gọi vào đây, không tự xử lý business logic.
 */
public class UserService {

    private final UserDAO userDAO;

    /** Constructor Injection — MainServer truyền DAO vào, không tự new. */
    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Đăng ký tài khoản mới.
     * Validate username/password, hash mật khẩu, lưu vào DB.
     */
    public void register(String username, String password, String role) throws Exception {
        Validation.validateUsername(username);
        Validation.validatePassword(password);

        User user = UserFactory.createUser(role, 0, username);
        user.setPasswordHash(PasswordUtil.hash(password));
        userDAO.insert(user);
    }

    /**
     * Đăng nhập: kiểm tra username + password + role.
     * Trả về User nếu hợp lệ, null nếu sai thông tin.
     */
    public User login(String username, String password, String role) throws Exception {
        User user = userDAO.findByUsername(username);
        if (user == null) return null;
        if (!PasswordUtil.verify(password, user.getPasswordHash())) return null;
        if (!user.getRole().equalsIgnoreCase(role)) return null;
        return user;
    }

    /**
     * Nạp tiền vào tài khoản của user.
     * Validate số tiền, cập nhật balance trong DB.
     * Trả về balance mới sau khi nạp.
     */
    public BigDecimal deposit(User user, double amount) throws Exception {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0!");
        }
        BigDecimal depositAmount = BigDecimal.valueOf(amount);
        user.updateBalance(depositAmount);
        userDAO.update(user);
        return user.getAccountBalance();
    }

    /**
     * Lấy thông tin user mới nhất từ DB (dùng để sync số dư).
     */
    public User findByUsername(String username) throws Exception {
        return userDAO.findByUsername(username);
    }

    /** Lấy toàn bộ danh sách user (dùng cho admin stats). */
    public java.util.List<server.models.users.User> findAll() throws Exception {
        return userDAO.findAll();
    }

}