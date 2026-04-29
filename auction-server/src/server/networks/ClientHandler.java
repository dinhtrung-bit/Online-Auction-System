package server.networks;

import com.google.gson.Gson;
import server.dao.interfaces.UserDAO;
import server.dao.impl.UserDAOimpl;
import server.models.users.User;
import server.models.users.UserFactory;
import server.models.users.Bidder;
import server.models.items.Item;
import server.networks.dto.MessageDTO;
import server.services.AuctionService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
    // OBSERVER PATTERN: Danh sách an toàn đa luồng quản lý tất cả các Client đang bật app
    private static final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    private Socket clientSocket;
    private Gson gson;
    private UserDAO userDAO;
    private PrintWriter out; // Kéo biến out ra ngoài để dùng cho việc Phát thanh

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.gson = new Gson();
        this.userDAO = new UserDAOimpl();

        // Vừa kết nối là chèn ngay vào danh sách quản lý
        activeClients.add(this);
    }

    // OBSERVER PATTERN: Hàm phát thanh tin nhắn tới TẤT CẢ mọi người
    private static void broadcast(String jsonMessage) {
        for (ClientHandler client : activeClients) {
            try {
                if (client.out != null) {
                    client.out.println(jsonMessage);
                }
            } catch (Exception e) {
                System.err.println("Lỗi gửi tin tới 1 client");
            }
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("Dang xu ly: " + clientSocket.getInetAddress());

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Server nhận yêu cầu: " + inputLine);

                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);
                MessageDTO response = new MessageDTO();

                if ("LOGIN".equals(request.getAction())) {
                    String[] credentials = request.getPayload().split(":");
                    if (credentials.length == 2) {
                        String username = credentials[0];
                        String password = credentials[1];
                        User user = userDAO.findByUsername(username);

                        if (user != null && password.equals(user.getPasswordHash())) {
                            response.setAction("LOGIN_SUCCESS");
                            response.setPayload(gson.toJson(user));
                            System.out.println("[Thành công] User đăng nhập: " + username);
                        } else {
                            response.setAction("LOGIN_FAILED");
                            response.setPayload("Sai tên đăng nhập hoặc mật khẩu!");
                        }
                    }
                }
                else if ("REGISTER".equals(request.getAction())) {
                    String[] data = request.getPayload().split(":");
                    if (data.length == 3) {
                        try {
                            User newUser = UserFactory.createUser(data[2], 0, data[0]);
                            userDAO.insert(newUser);
                            response.setAction("REGISTER_SUCCESS");
                            response.setPayload("Đăng ký tài khoản thành công!");
                        } catch (Exception ex) {
                            response.setAction("REGISTER_FAILED");
                            response.setPayload("Tên đăng nhập đã tồn tại hoặc lỗi hệ thống.");
                        }
                    }
                }
                else if ("CREATE_AUCTION".equals(request.getAction())) {
                    try {
                        Item newItem = gson.fromJson(request.getPayload(), Item.class);
                        LocalDateTime endTime = LocalDateTime.now().plusHours(24);
                        AuctionService.getInstance().createNewAuction(newItem, endTime);

                        response.setAction("CREATE_AUCTION_SUCCESS");
                        response.setPayload("Tạo phiên đấu giá thành công!");
                    } catch (Exception e) {
                        response.setAction("CREATE_AUCTION_FAILED");
                        response.setPayload("Lỗi hệ thống khi tạo phiên đấu giá: " + e.getMessage());
                    }
                }
                // CHỨC NĂNG ĐẶT GIÁ (ĐÃ TÍCH HỢP ĐỒNG BỘ VÀ PHÁT THANH)
                else if ("BID".equals(request.getAction())) {
                    String[] data = request.getPayload().split(":");

                    if (data.length == 3) {
                        try {
                            Long roomId = Long.parseLong(data[0]);
                            String username = data[1];
                            double amount = Double.parseDouble(data[2]);

                            User user = userDAO.findByUsername(username);

                            if (user != null) {
                                Bidder bidder = new Bidder(user.getUserId(), user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getAccountBalance());
                                String result = AuctionService.getInstance().handleBidRequest(roomId, bidder, amount);

                                if ("SUCCESS".equals(result)) {
                                    // 1. Trả lời riêng cho người vừa đặt là thành công
                                    response.setAction("BID_SUCCESS");
                                    response.setPayload("Đặt giá thành công!");
                                    out.println(gson.toJson(response)); // Gửi ngay

                                    System.out.println("[Thành công] " + username + " đặt " + amount + " cho phòng " + roomId);

                                    // 2. OBSERVER: Phát thanh giá mới tới TẤT CẢ mọi người
                                    MessageDTO updateMsg = new MessageDTO();
                                    updateMsg.setAction("UPDATE_PRICE");
                                    updateMsg.setPayload(roomId + ":" + amount + ":" + username);
                                    broadcast(gson.toJson(updateMsg));

                                    continue; // Đã gửi response rồi nên bỏ qua lệnh gửi ở cuối vòng lặp
                                } else {
                                    response.setAction("BID_FAILED");
                                    response.setPayload(result);
                                }
                            } else {
                                response.setAction("BID_FAILED");
                                response.setPayload("Không tìm thấy thông tin người dùng.");
                            }
                        } catch (Exception e) {
                            response.setAction("BID_FAILED");
                            response.setPayload("Lỗi hệ thống khi đặt giá.");
                        }
                    }
                }
                else {
                    response.setAction("ERROR");
                    response.setPayload("Hành động không được hỗ trợ.");
                }

                // Gửi response cho các trường hợp không phải BID_SUCCESS
                String jsonResponse = gson.toJson(response);
                out.println(jsonResponse);
            }
        } catch (Exception e) {
            System.out.println("Client đã ngắt kết nối: " + clientSocket.getInetAddress());
        } finally {
            // RẤT QUAN TRỌNG: Xóa khách khỏi danh sách khi họ tắt app để giải phóng RAM
            activeClients.remove(this);
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}