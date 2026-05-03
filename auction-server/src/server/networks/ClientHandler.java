package server.networks;

import com.google.gson.Gson;
import server.networks.dto.MessageDTO;
import server.dao.impl.UserDAOimpl;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.users.Bidder; // Đã thêm import Bidder
import server.models.users.User;
import server.models.users.UserFactory;
import server.services.AuctionService;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {
    private static final CopyOnWriteArrayList<ClientHandler> activeClients = new CopyOnWriteArrayList<>();
    private final Socket clientSocket;
    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAOimpl();
    private PrintWriter out;
    private User loggedInUser = null;

    private final Map<String, RequestProcessor> processors = new HashMap<>();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        activeClients.add(this);
        initProcessors();
    }

    private void initProcessors() {
        processors.put("LOGIN",                   this::handleLogin);
        processors.put("REGISTER",                this::handleRegister);
        processors.put("BID",                     this::handleBid);
        processors.put("GET_AUCTION_DETAIL",      this::handleGetDetail);
        processors.put("GET_AVAILABLE_AUCTIONS",  this::handleGetAvailableAuctions);
        processors.put("GET_ALL_AUCTIONS",        this::handleGetAllAuctions);
        processors.put("GET_ALL_USERS",           this::handleGetAllUsers);
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);
                RequestProcessor processor = processors.getOrDefault(request.getAction(),
                        (req) -> new MessageDTO("ERROR", "Hành động không hợp lệ"));

                MessageDTO response = processor.process(request);
                if (response != null) {
                    out.println(gson.toJson(response));
                }
            }
        } catch (IOException e) {
            System.err.println(">>> Kết nối với Client bị ngắt.");
        } finally {
            cleanup();
        }
    }

    private MessageDTO handleLogin(MessageDTO request) {
        try {
            String[] credentials = request.getPayload().split(":");
            if (credentials.length < 3) return new MessageDTO("LOGIN_FAILED", "Thông tin không đủ");

            String role = credentials[0];
            String username = credentials[1];
            String password = credentials[2];

            // Xử lý triệt để Exception từ DAO tại đây
            User user = userDAO.findByUsername(username);

            if (user != null && user.getPasswordHash().equals(password) && user.getRole().equalsIgnoreCase(role)) {
                this.loggedInUser = user;
                return new MessageDTO("LOGIN_SUCCESS", gson.toJson(user));
            }
        } catch (Exception e) {
            return new MessageDTO("LOGIN_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
        return new MessageDTO("LOGIN_FAILED", "Sai tài khoản hoặc mật khẩu");
    }

    private MessageDTO handleRegister(MessageDTO request) {
        try {
            String[] data = request.getPayload().split(":");
            User newUser = UserFactory.createUser(data[2], 0, data[0]);
            newUser.setPasswordHash(data[1]);

            userDAO.insert(newUser);
            return new MessageDTO("REGISTER_SUCCESS", "Đăng ký thành công!");
        } catch (Exception e) {
            return new MessageDTO("REGISTER_FAILED", "Lỗi đăng ký: " + e.getMessage());
        }
    }

    private MessageDTO handleGetDetail(MessageDTO request) {
        try {
            long roomId = Long.parseLong(request.getPayload().trim());
            AuctionRoom room = AuctionService.getInstance().getActiveRooms()
                    .stream().filter(r -> r.getId() == roomId).findFirst().orElse(null);

            if (room == null) return new MessageDTO("ERROR", "Không tìm thấy phòng: " + roomId);

            long secondsLeft = Math.max(0,
                    Duration.between(LocalDateTime.now(), room.getEndTime()).getSeconds());
            String price = room.getCurrentPrice() != null
                    ? room.getCurrentPrice().toPlainString()
                    : room.getItem().getStartingPrice().toPlainString();
            String status = room.getStatus().name();

            return new MessageDTO("AUCTION_DETAIL_DATA", price + ":" + secondsLeft + ":" + status);
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy chi tiết: " + e.getMessage());
        }
    }

    // Trả danh sách phòng đang OPEN hoặc RUNNING — dành cho Bidder xem
    private MessageDTO handleGetAvailableAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = AuctionService.getInstance().getActiveRooms()
                    .stream()
                    .filter(r -> r.getStatus() == AuctionStatus.OPEN
                            || r.getStatus() == AuctionStatus.RUNNING)
                    .map(this::roomToMap)
                    .collect(Collectors.toList());
            return new MessageDTO("AUCTION_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách: " + e.getMessage());
        }
    }

    // Trả toàn bộ phòng — dành cho Seller/Admin xem
    private MessageDTO handleGetAllAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = AuctionService.getInstance().getActiveRooms()
                    .stream()
                    .map(this::roomToMap)
                    .collect(Collectors.toList());
            return new MessageDTO("AUCTION_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách: " + e.getMessage());
        }
    }

    // Trả danh sách user — chỉ Admin mới được gọi
    private MessageDTO handleGetAllUsers(MessageDTO request) {
        if (loggedInUser == null || !loggedInUser.getRole().equalsIgnoreCase("ADMIN")) {
            return new MessageDTO("ERROR", "Không có quyền truy cập!");
        }
        try {
            List<User> users = userDAO.findAll();
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

    // Helper: chuyển AuctionRoom thành Map để Gson serialize gọn gàng
    private Map<String, Object> roomToMap(AuctionRoom room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           room.getId());
        m.put("itemName",     room.getItem() != null ? room.getItem().getName() : "N/A");
        m.put("currentPrice", room.getCurrentPrice() != null
                ? room.getCurrentPrice().doubleValue()
                : (room.getItem() != null ? room.getItem().getStartingPrice().doubleValue() : 0));
        m.put("currentWinner", room.getCurrentWinner() != null
                ? room.getCurrentWinner().getUsername() : "Chưa có");
        m.put("status",       room.getStatus().name());
        return m;
    }

    private MessageDTO handleBid(MessageDTO request) {
        if (this.loggedInUser == null) return new MessageDTO("BID_FAILED", "Vui lòng đăng nhập");

        try {
            String[] data = request.getPayload().split(":");
            String roomId = data[0];
            String userBid = data[1];
            String amount = data[2];

            // 1. Kiểm tra đối tượng gửi request có phải Bidder không
            if (!(this.loggedInUser instanceof Bidder)) {
                return new MessageDTO("BID_FAILED", "Chỉ người dùng Bidder mới được phép đặt giá!");
            }

            // 2. Gọi AuctionService để xác thực logic, cập nhật giá và lưu database
            String result = AuctionService.getInstance().handleBidRequest(
                    Long.parseLong(roomId),
                    (Bidder) this.loggedInUser,
                    Double.parseDouble(amount)
            );

            // 3. Chỉ broadcast giá mới nếu AuctionService xử lý hợp lệ
            if ("SUCCESS".equals(result)) {
                MessageDTO updateMsg = new MessageDTO("UPDATE_PRICE", roomId + ":" + amount + ":" + userBid);
                broadcast(gson.toJson(updateMsg));

                return new MessageDTO("BID_SUCCESS", "Đặt giá thành công");
            } else {
                // Trả về lý do lỗi từ AuctionService (ví dụ: giá thấp hơn giá hiện tại)
                return new MessageDTO("BID_FAILED", result);
            }
        } catch (Exception e) {
            return new MessageDTO("BID_FAILED", "Lỗi xử lý đặt giá: " + e.getMessage());
        }
    }

    public static void broadcast(String json) {
        activeClients.forEach(c -> {
            if (c.out != null) c.out.println(json);
        });
    }

    private void cleanup() {
        activeClients.remove(this);
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    interface RequestProcessor {
        MessageDTO process(MessageDTO request);
    }
}