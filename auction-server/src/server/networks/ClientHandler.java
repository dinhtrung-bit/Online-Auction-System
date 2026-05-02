package server.networks;

import com.google.gson.Gson;
import server.networks.dto.MessageDTO;
import server.dao.impl.UserDAOimpl;
import server.dao.interfaces.UserDAO;
import server.models.users.User;
import server.models.users.UserFactory;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
        processors.put("LOGIN", this::handleLogin);
        processors.put("REGISTER", this::handleRegister);
        processors.put("BID", this::handleBid);
        processors.put("GET_AUCTION_DETAIL", this::handleGetDetail);
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
        // Logic lấy dữ liệu thật: Giá hiện tại | Thời gian còn lại (s) | Trạng thái
        // Thành có thể gọi AuctionDAO tại đây bọc trong try-catch tương tự
        String roomId = request.getPayload();
        String realTimeData = "18200000:3600:OPENING";
        return new MessageDTO("AUCTION_DETAIL_DATA", realTimeData);
    }

    private MessageDTO handleBid(MessageDTO request) {
        if (this.loggedInUser == null) return new MessageDTO("BID_FAILED", "Vui lòng đăng nhập");

        try {
            String[] data = request.getPayload().split(":");
            String roomId = data[0];
            String amount = data[2];
            String userBid = data[1];

            // Gửi cập nhật cho tất cả mọi người
            MessageDTO updateMsg = new MessageDTO("UPDATE_PRICE", roomId + ":" + amount + ":" + userBid);
            broadcast(gson.toJson(updateMsg));

            return new MessageDTO("BID_SUCCESS", "Đặt giá thành công");
        } catch (Exception e) {
            return new MessageDTO("BID_FAILED", "Lỗi đặt giá");
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