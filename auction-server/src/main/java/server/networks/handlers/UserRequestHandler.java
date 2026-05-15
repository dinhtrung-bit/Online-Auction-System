package server.networks.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import server.models.finance.DepositRequest;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.UserService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserRequestHandler {

    private final UserService userService;
    private final Gson gson = new Gson();

    public UserRequestHandler(UserService userService) {
        this.userService = userService;
    }

    public MessageDTO handleLogin(MessageDTO request, UserHolder userHolder) {
        try {
            Map<String, Object> data = parseJsonPayload(request);

            String role = getString(data, "role", "");
            String username = getString(data, "username", "");
            String password = getString(data, "password", "");

            if (username.isBlank() || password.isBlank() || role.isBlank()) {
                return new MessageDTO("LOGIN_FAILED", "Thiếu username, password hoặc role.");
            }

            User user = userService.login(username, password, role);
            if (user == null) {
                return new MessageDTO("LOGIN_FAILED", "Sai tài khoản, mật khẩu hoặc vai trò.");
            }

            userHolder.setUser(user);
            return new MessageDTO("LOGIN_SUCCESS", gson.toJson(user));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("LOGIN_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("LOGIN_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleRegister(MessageDTO request) {
        try {
            Map<String, Object> data = parseJsonPayload(request);

            String username = getString(data, "username", "");
            String password = getString(data, "password", "");
            String role = getString(data, "role", "");

            if (username.isBlank() || password.isBlank() || role.isBlank()) {
                return new MessageDTO("REGISTER_FAILED", "Thiếu username, password hoặc role.");
            }

            userService.register(username, password, role);
            return new MessageDTO("REGISTER_SUCCESS", "Đăng ký thành công!");

        } catch (server.exceptions.DuplicateDataException e) {
            return new MessageDTO("REGISTER_FAILED", "Tên đăng nhập đã tồn tại!");
        } catch (IllegalArgumentException e) {
            return new MessageDTO("REGISTER_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("REGISTER_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAllUsers(MessageDTO request, User loggedInUser) {
        if (!isAdmin(loggedInUser)) {
            return new MessageDTO("ERROR", "Không có quyền Admin!");
        }

        try {
            List<User> users = userService.findAll();

            List<Map<String, Object>> result = users.stream().map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getUserId());
                m.put("username", u.getUsername());
                m.put("role", u.getRole());
                m.put("status", "ACTIVE");
                m.put("balance", u.getAccountBalance() != null ? u.getAccountBalance().doubleValue() : 0);
                return m;
            }).collect(Collectors.toList());

            return new MessageDTO("USER_LIST", gson.toJson(result));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách user: " + e.getMessage());
        }
    }

    public MessageDTO handleGetBalance(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập.");
        }

        try {
            User freshUser = userService.findByUsername(loggedInUser.getUsername());
            if (freshUser != null) {
                userHolder.setUser(freshUser);
                return new MessageDTO("BALANCE_DATA", freshUser.getAccountBalance().toPlainString());
            }

            return new MessageDTO("BALANCE_DATA", loggedInUser.getAccountBalance().toPlainString());

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy số dư: " + e.getMessage());
        }
    }

    public MessageDTO handleDeposit(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) {
            return new MessageDTO("DEPOSIT_FAILED", "Chưa đăng nhập.");
        }

        try {
            Map<String, Object> data = parseJsonPayload(request);

            BigDecimal amount = getBigDecimal(data, "amount");
            String note = getString(data, "note", "");

            DepositRequest created = userService.createDepositRequest(loggedInUser, amount, note);
            return new MessageDTO("DEPOSIT_REQUEST_CREATED", gson.toJson(toMap(created)));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("DEPOSIT_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyDepositRequests(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập.");
        }

        try {
            List<Map<String, Object>> list = userService.findDepositRequestsByUser(loggedInUser.getUserId())
                    .stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            return new MessageDTO("MY_DEPOSIT_REQUESTS", gson.toJson(list));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy lịch sử nạp tiền: " + e.getMessage());
        }
    }

    public MessageDTO handleGetDepositRequests(MessageDTO request, User admin) {
        if (!isAdmin(admin)) {
            return new MessageDTO("ERROR", "Không có quyền Admin!");
        }

        try {
            List<Map<String, Object>> list = userService.findAllDepositRequests()
                    .stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            return new MessageDTO("DEPOSIT_REQUEST_LIST", gson.toJson(list));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy yêu cầu nạp tiền: " + e.getMessage());
        }
    }

    public MessageDTO handleGetPendingDeposits(MessageDTO request, User admin) {
        if (!isAdmin(admin)) {
            return new MessageDTO("ERROR", "Không có quyền Admin!");
        }

        try {
            List<Map<String, Object>> list = userService.findPendingDepositRequests()
                    .stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            return new MessageDTO("DEPOSIT_REQUEST_LIST", gson.toJson(list));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy yêu cầu chờ duyệt: " + e.getMessage());
        }
    }

    public MessageDTO handleApproveDeposit(MessageDTO request, User admin) {
        if (!isAdmin(admin)) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", "Không có quyền Admin!");
        }

        try {
            Map<String, Object> data = parseJsonPayload(request);

            int requestId = getInt(data, "requestId");
            String note = getString(data, "adminNote", "Đã duyệt");

            BigDecimal newBalance = userService.approveDepositRequest(requestId, admin, note);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestId", requestId);
            result.put("newBalance", newBalance.doubleValue());
            result.put("message", "Đã duyệt yêu cầu nạp tiền #" + requestId);

            return new MessageDTO("DEPOSIT_APPROVED", gson.toJson(result));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleRejectDeposit(MessageDTO request, User admin) {
        if (!isAdmin(admin)) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", "Không có quyền Admin!");
        }

        try {
            Map<String, Object> data = parseJsonPayload(request);

            int requestId = getInt(data, "requestId");
            String note = getString(data, "adminNote", "Không hợp lệ");

            userService.rejectDepositRequest(requestId, admin, note);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestId", requestId);
            result.put("message", "Đã từ chối yêu cầu nạp tiền #" + requestId);

            return new MessageDTO("DEPOSIT_REJECTED", gson.toJson(result));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleAdminAdjustBalance(MessageDTO request, User admin) {
        if (!isAdmin(admin)) {
            return new MessageDTO("ADMIN_BALANCE_FAILED", "Không có quyền Admin!");
        }

        try {
            Map<String, Object> data = parseJsonPayload(request);

            int userId = getInt(data, "userId");
            BigDecimal delta = getBigDecimal(data, "delta");
            String reason = getString(data, "reason", "Điều chỉnh bởi Admin");

            BigDecimal newBalance = userService.adminAdjustBalance(userId, delta, admin, reason);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userId", userId);
            result.put("newBalance", newBalance.doubleValue());
            result.put("message", "Đã điều chỉnh ví người dùng #" + userId);

            return new MessageDTO("ADMIN_BALANCE_UPDATED", gson.toJson(result));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("ADMIN_BALANCE_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("ADMIN_BALANCE_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleGetDepositStats(MessageDTO request, User admin) {
        if (!isAdmin(admin)) {
            return new MessageDTO("ERROR", "Không có quyền Admin!");
        }

        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("pendingCount", userService.countPendingDeposits());
            stats.put("approvedAmount", userService.sumDepositsByStatus("APPROVED").doubleValue());
            stats.put("pendingAmount", userService.sumDepositsByStatus("PENDING").doubleValue());
            stats.put("rejectedAmount", userService.sumDepositsByStatus("REJECTED").doubleValue());

            return new MessageDTO("DEPOSIT_STATS", gson.toJson(stats));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy thống kê nạp tiền: " + e.getMessage());
        }
    }

    private Map<String, Object> toMap(DepositRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", r.getId());
        m.put("userId", r.getUserId());
        m.put("username", r.getUsername() != null ? r.getUsername() : "User #" + r.getUserId());
        m.put("amount", r.getAmount() != null ? r.getAmount().doubleValue() : 0);
        m.put("status", r.getStatus());
        m.put("note", r.getNote() != null ? r.getNote() : "");
        m.put("adminId", r.getAdminId());
        m.put("adminNote", r.getAdminNote() != null ? r.getAdminNote() : "");
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        m.put("reviewedAt", r.getReviewedAt() != null ? r.getReviewedAt().toString() : "");
        return m;
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonPayload(MessageDTO request) {
        if (request == null || request.getPayload() == null || request.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("Payload không được để trống.");
        }

        String payload = request.getPayload().trim();
        if (!payload.startsWith("{")) {
            throw new IllegalArgumentException("Payload phải là JSON object hợp lệ.");
        }

        try {
            Map<String, Object> data = gson.fromJson(payload, Map.class);
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("Payload JSON không hợp lệ.");
            }
            return data;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Payload JSON sai định dạng.");
        }
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;

        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private int getInt(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số nguyên.");
        }
    }

    private BigDecimal getBigDecimal(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số hợp lệ.");
        }
    }
}