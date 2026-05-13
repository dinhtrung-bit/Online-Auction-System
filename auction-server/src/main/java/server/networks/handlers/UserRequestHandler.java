package server.networks.handlers;

import com.google.gson.Gson;
import server.models.finance.DepositRequest;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.UserService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UserRequestHandler — xử lý user, ví tiền và quyền Admin.
 * V6: nạp tiền chuyển sang request chờ Admin duyệt.
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
            Map<String,String> data = gson.fromJson(request.getPayload(), Map.class);
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
            Map<String,String> data = gson.fromJson(request.getPayload(), Map.class);
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
                m.put("balance",  u.getAccountBalance() != null ? u.getAccountBalance().doubleValue() : 0);
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

    /**
     * V6: DEPOSIT chỉ tạo yêu cầu chờ Admin duyệt, không cộng tiền ngay.
     */
    @SuppressWarnings("unchecked")
    public MessageDTO handleDeposit(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) return new MessageDTO("DEPOSIT_FAILED", "Chưa đăng nhập");
        try {
            BigDecimal amount;
            String note = "";
            String payload = request.getPayload() == null ? "" : request.getPayload().trim();
            if (payload.startsWith("{")) {
                Map<String, Object> data = gson.fromJson(payload, Map.class);
                Object amountRaw = data != null ? data.get("amount") : null;
                amount = new BigDecimal(String.valueOf(amountRaw));
                note = data != null && data.get("note") != null ? String.valueOf(data.get("note")) : "";
            } else {
                amount = new BigDecimal(payload);
            }
            DepositRequest created = userService.createDepositRequest(loggedInUser, amount, note);
            return new MessageDTO("DEPOSIT_REQUEST_CREATED", gson.toJson(toMap(created)));
        } catch (IllegalArgumentException e) {
            return new MessageDTO("DEPOSIT_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyDepositRequests(MessageDTO request, UserHolder userHolder) {
        User loggedInUser = userHolder.getUser();
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            List<Map<String, Object>> list = userService.findDepositRequestsByUser(loggedInUser.getUserId())
                    .stream().map(this::toMap).collect(Collectors.toList());
            return new MessageDTO("MY_DEPOSIT_REQUESTS", gson.toJson(list));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy lịch sử nạp tiền: " + e.getMessage());
        }
    }

    public MessageDTO handleGetDepositRequests(MessageDTO request, User admin) {
        if (!isAdmin(admin)) return new MessageDTO("ERROR", "Không có quyền Admin!");
        try {
            List<Map<String, Object>> list = userService.findAllDepositRequests()
                    .stream().map(this::toMap).collect(Collectors.toList());
            return new MessageDTO("DEPOSIT_REQUEST_LIST", gson.toJson(list));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy yêu cầu nạp tiền: " + e.getMessage());
        }
    }

    public MessageDTO handleGetPendingDeposits(MessageDTO request, User admin) {
        if (!isAdmin(admin)) return new MessageDTO("ERROR", "Không có quyền Admin!");
        try {
            List<Map<String, Object>> list = userService.findPendingDepositRequests()
                    .stream().map(this::toMap).collect(Collectors.toList());
            return new MessageDTO("DEPOSIT_REQUEST_LIST", gson.toJson(list));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy yêu cầu chờ duyệt: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public MessageDTO handleApproveDeposit(MessageDTO request, User admin) {
        if (!isAdmin(admin)) return new MessageDTO("DEPOSIT_REVIEW_FAILED", "Không có quyền Admin!");
        try {
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);
            int requestId = toInt(data.get("requestId"));
            String note = data.get("adminNote") != null ? String.valueOf(data.get("adminNote")) : "Đã duyệt";
            BigDecimal newBalance = userService.approveDepositRequest(requestId, admin, note);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestId", requestId);
            result.put("newBalance", newBalance.doubleValue());
            result.put("message", "Đã duyệt yêu cầu nạp tiền #" + requestId);
            return new MessageDTO("DEPOSIT_APPROVED", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public MessageDTO handleRejectDeposit(MessageDTO request, User admin) {
        if (!isAdmin(admin)) return new MessageDTO("DEPOSIT_REVIEW_FAILED", "Không có quyền Admin!");
        try {
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);
            int requestId = toInt(data.get("requestId"));
            String note = data.get("adminNote") != null ? String.valueOf(data.get("adminNote")) : "Không hợp lệ";
            userService.rejectDepositRequest(requestId, admin, note);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestId", requestId);
            result.put("message", "Đã từ chối yêu cầu nạp tiền #" + requestId);
            return new MessageDTO("DEPOSIT_REJECTED", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_REVIEW_FAILED", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public MessageDTO handleAdminAdjustBalance(MessageDTO request, User admin) {
        if (!isAdmin(admin)) return new MessageDTO("ADMIN_BALANCE_FAILED", "Không có quyền Admin!");
        try {
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);
            int userId = toInt(data.get("userId"));
            BigDecimal delta = new BigDecimal(String.valueOf(data.get("delta")));
            String reason = data.get("reason") != null ? String.valueOf(data.get("reason")) : "Điều chỉnh bởi Admin";
            BigDecimal newBalance = userService.adminAdjustBalance(userId, delta, admin, reason);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userId", userId);
            result.put("newBalance", newBalance.doubleValue());
            result.put("message", "Đã điều chỉnh ví người dùng #" + userId);
            return new MessageDTO("ADMIN_BALANCE_UPDATED", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ADMIN_BALANCE_FAILED", e.getMessage());
        }
    }

    public MessageDTO handleGetDepositStats(MessageDTO request, User admin) {
        if (!isAdmin(admin)) return new MessageDTO("ERROR", "Không có quyền Admin!");
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

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
}
