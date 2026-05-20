package server.networks.handlers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import server.exceptions.DuplicateDataException;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.UserService;

/**
 * UserRequestHandler — parse request và ủy quyền cho UserService.
 *
 * Thay đổi so với phiên bản cũ:
 *   - isAdmin() dùng user.canAdmin() thay vì so sánh chuỗi "ADMIN".equalsIgnoreCase(role).
 *     Nhất quán với cách Admin model tự khai báo quyền hạn của mình.
 */
public class UserRequestHandler {

    private final UserService userService;
    private final Gson        gson = new Gson();

    public UserRequestHandler(UserService userService) {
        this.userService = userService;
    }

    public MessageDTO handleLogin(MessageDTO request, UserHolder userHolder) {
        try {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);
            String role     = PayloadParser.getString(data, "role", "");
            String username = PayloadParser.getString(data, "username", "");
            String password = PayloadParser.getString(data, "password", "");

            if (username.isBlank() || password.isBlank() || role.isBlank()) {
                return new MessageDTO("LOGIN_FAILED", "Thiếu username, password hoặc role.");
            }

            User user = userService.login(username, password, role);
            if (user == null) {
                return new MessageDTO("LOGIN_FAILED", "Sai tài khoản, mật khẩu hoặc vai trò.");
            }

            userHolder.setUser(user);
            return new MessageDTO("LOGIN_SUCCESS", gson.toJson(user));

        } catch (Exception e) {
            return new MessageDTO("LOGIN_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleRegister(MessageDTO request) {
        try {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);
            String username = PayloadParser.getString(data, "username", "");
            String password = PayloadParser.getString(data, "password", "");
            String role     = PayloadParser.getString(data, "role", "");

            if (username.isBlank() || password.isBlank() || role.isBlank()) {
                return new MessageDTO("REGISTER_FAILED", "Thiếu username, password hoặc role.");
            }

            userService.register(username, password, role);
            return new MessageDTO("REGISTER_SUCCESS", "Đăng ký thành công!");

        } catch (DuplicateDataException e) {
            return new MessageDTO("REGISTER_FAILED", "Tên đăng nhập đã tồn tại!");
        } catch (IllegalArgumentException e) {
            return new MessageDTO("REGISTER_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("REGISTER_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAllUsers(MessageDTO request, User loggedInUser) {
        if (!loggedInUser.canAdmin()) {
            return new MessageDTO("ERROR", "Không có quyền Admin!");
        }
        try {
            List<Map<String, Object>> result = userService.findAll().stream()
                    .map(this::toUserMap).collect(Collectors.toList());
            return new MessageDTO("USER_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách user: " + e.getMessage());
        }
    }

    public MessageDTO handleGetBalance(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập.");
        try {
            User fresh = userService.findByUsername(loggedInUser.getUsername());
            if (fresh != null) {
                userHolder.setUser(fresh);
                return new MessageDTO("BALANCE_DATA", fresh.getAccountBalance().toPlainString());
            }
            return new MessageDTO("BALANCE_DATA", loggedInUser.getAccountBalance().toPlainString());
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy số dư: " + e.getMessage());
        }
    }

    public MessageDTO handleSelfDeposit(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) return new MessageDTO("DEPOSIT_FAILED", "Chưa đăng nhập.");
        try {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);
            BigDecimal amount = PayloadParser.getBigDecimal(data, "amount");

            BigDecimal newBalance = userService.selfDeposit(loggedInUser, amount);

            User fresh = userService.findById(loggedInUser.getUserId());
            if (fresh != null) userHolder.setUser(fresh);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("newBalance", newBalance.doubleValue());
            result.put("message",    "Nạp tiền thành công!");
            return new MessageDTO("DEPOSIT_SUCCESS", gson.toJson(result));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("DEPOSIT_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleAdminAdjustBalance(MessageDTO request, User admin) {
        if (!admin.canAdmin()) {
            return new MessageDTO("ADMIN_BALANCE_FAILED", "Không có quyền Admin!");
        }
        try {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);
            int userId       = PayloadParser.getInt(data, "userId");
            BigDecimal delta = PayloadParser.getBigDecimalAllowNegative(data, "delta");
            String reason    = PayloadParser.getString(data, "reason", "Điều chỉnh bởi Admin");

            BigDecimal newBalance = userService.adminAdjustBalance(userId, delta, admin, reason);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userId",     userId);
            result.put("newBalance", newBalance.doubleValue());
            result.put("message",    "Đã điều chỉnh ví người dùng #" + userId);
            return new MessageDTO("ADMIN_BALANCE_UPDATED", gson.toJson(result));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("ADMIN_BALANCE_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("ADMIN_BALANCE_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toUserMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",       u.getUserId());
        m.put("username", u.getUsername());
        m.put("role",     u.getRole());
        m.put("balance",  u.getAccountBalance() != null ? u.getAccountBalance().doubleValue() : 0);
        return m;
    }
}
