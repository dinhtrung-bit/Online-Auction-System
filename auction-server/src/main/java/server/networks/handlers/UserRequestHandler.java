package server.networks.handlers;

import com.google.gson.Gson;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.UserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UserRequestHandler — xử lý tất cả request liên quan đến người dùng:
 *   LOGIN, REGISTER, GET_ALL_USERS, GET_ADMIN_STATS (phần user), GET_BALANCE, DEPOSIT
 *
 * Chỉ biết đến UserService — không gọi bất kỳ DAO nào trực tiếp.
 * Tuân thủ: Single Responsibility Principle + Layered Architecture.
 */
public class UserRequestHandler {

    private final UserService userService;
    private final Gson gson = new Gson();

    public UserRequestHandler(UserService userService) {
        this.userService = userService;
    }

    @SuppressWarnings("unchecked")
    public MessageDTO handleLogin(MessageDTO request, UserHolder userHolder) {
        try {
            // [Fix] Parse JSON thay vì split(":") — an toàn khi password chứa ":"
            java.util.Map<String,String> data =
                    gson.fromJson(request.getPayload(), java.util.Map.class);
            if (data == null || !data.containsKey("username"))
                return new MessageDTO("LOGIN_FAILED", "Thông tin không đủ");
            String role     = data.getOrDefault("role",     "");
            String username = data.getOrDefault("username", "");
            String password = data.getOrDefault("password", "");
            User user = userService.login(username, password, role);
            if (user != null) {
                userHolder.setUser(user);
                return new MessageDTO("LOGIN_SUCCESS", gson.toJson(user));
            }
        } catch (Exception e) {
            return new MessageDTO("LOGIN_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
        return new MessageDTO("LOGIN_FAILED", "Sai tài khoản hoặc mật khẩu");
    }

    @SuppressWarnings("unchecked")
    public MessageDTO handleRegister(MessageDTO request) {
        try {
            // [Fix] Parse JSON thay vì split(":") — an toàn khi dữ liệu chứa ":"
            java.util.Map<String,String> data =
                    gson.fromJson(request.getPayload(), java.util.Map.class);
            if (data == null || !data.containsKey("username"))
                return new MessageDTO("REGISTER_FAILED", "Dữ liệu đăng ký không đủ!");
            String username = data.getOrDefault("username", "").trim();
            String password = data.getOrDefault("password", "");
            String role     = data.getOrDefault("role",     "").trim();
            userService.register(username, password, role);
            return new MessageDTO("REGISTER_SUCCESS", "Đăng ký thành công!");
        } catch (server.exceptions.DuplicateDataException e) {
            return new MessageDTO("REGISTER_FAILED", "Tên đăng nhập đã tồn tại!");
        } catch (IllegalArgumentException e) {
            return new MessageDTO("REGISTER_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("REGISTER_FAILED", "Lỗi đăng ký: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAllUsers(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null || !loggedInUser.getRole().equalsIgnoreCase("ADMIN"))
            return new MessageDTO("ERROR", "Không có quyền truy cập!");
        try {
            List<User> users = userService.findAll();
            List<Map<String, Object>> result = users.stream().map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",       u.getUserId());
                m.put("username", u.getUsername());
                m.put("role",     u.getRole());
                m.put("status",   "ACTIVE");
                return m;
            }).collect(Collectors.toList());
            return new MessageDTO("USER_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách user: " + e.getMessage());
        }
    }

    public MessageDTO handleGetBalance(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            User freshUser = userService.findByUsername(loggedInUser.getUsername());
            if (freshUser != null) {
                userHolder.setUser(freshUser);
                return new MessageDTO("BALANCE_DATA", freshUser.getAccountBalance().toPlainString());
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy số dư: " + e.getMessage());
        }
        return new MessageDTO("BALANCE_DATA", loggedInUser.getAccountBalance().toPlainString());
    }

    public MessageDTO handleDeposit(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            double amount = Double.parseDouble(request.getPayload().trim());
            java.math.BigDecimal newBalance = userService.deposit(loggedInUser, amount);
            System.out.println(">>> [Nạp tiền] " + loggedInUser.getUsername() + " nạp " + amount);
            return new MessageDTO("DEPOSIT_SUCCESS", newBalance.toPlainString());
        } catch (IllegalArgumentException e) {
            return new MessageDTO("DEPOSIT_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_FAILED", "Lỗi: " + e.getMessage());
        }
    }
}