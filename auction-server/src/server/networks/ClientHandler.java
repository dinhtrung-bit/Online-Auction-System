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

    // OBSERVER PATTERN: Danh sách an toàn đa luồng quản lý các Client đang bật app
    private static final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    private Socket clientSocket;
    private Gson gson;
    private UserDAO userDAO;
    private PrintWriter out;

    // VÁ LỖI 3.1.1: Lưu trữ trạng thái đăng nhập của Client này để phân quyền
    private User loggedInUser = null;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.gson = new Gson();
        this.userDAO = new UserDAOimpl();
        // Vừa kết nối là đưa ngay vào danh sách quản lý
        activeClients.add(this);
    }

    // OBSERVER PATTERN: Hàm phát thanh tin nhắn tới toàn bộ người dùng
    private static void broadcast(String jsonMessage) {
        for (ClientHandler client : activeClients) {
            try {
                if (client.out != null) {
                    client.out.println(jsonMessage);
                }
            } catch (Exception e) {
                System.err.println("Lỗi gửi tin tới 1 client: " + e.getMessage());
            }
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("Đang xử lý kết nối từ: " + clientSocket.getInetAddress());
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                System.out.println(">>> Server nhận yêu cầu: " + inputLine);
                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);
                MessageDTO response = new MessageDTO();

                // ================= XỬ LÝ ĐĂNG NHẬP =================
                if ("LOGIN".equals(request.getAction())) {
                    String[] credentials = request.getPayload().split(":");
                    if (credentials.length == 2) {
                        String username = credentials[0];
                        String password = credentials[1];
                        User user = userDAO.findByUsername(username);

                        if (user != null && password.equals(user.getPasswordHash())) {
                            // GHI NHỚ NGƯỜI DÙNG: Lưu thông tin để phân quyền cho các request sau
                            this.loggedInUser = user;

                            response.setAction("LOGIN_SUCCESS");
                            response.setPayload(gson.toJson(user));
                            System.out.println("[Thành công] User đăng nhập: " + username + " (Vai trò: " + user.getRole() + ")");
                        } else {
                            response.setAction("LOGIN_FAILED");
                            response.setPayload("Sai tên đăng nhập hoặc mật khẩu!");
                        }
                    } else {
                        response.setAction("LOGIN_FAILED");
                        response.setPayload("Sai định dạng dữ liệu đăng nhập!");
                    }
                }

                // ================= XỬ LÝ ĐĂNG KÝ =================
                else if ("REGISTER".equals(request.getAction())) {
                    String[] data = request.getPayload().split(":");
                    if (data.length == 3) {
                        try {
                            // data[0]: username, data[1]: password, data[2]: role
                            User newUser = UserFactory.createUser(data[2], 0, data[0]);
                            newUser.setPasswordHash(data[1]); // Cập nhật mật khẩu
                            userDAO.insert(newUser);

                            response.setAction("REGISTER_SUCCESS");
                            response.setPayload("Đăng ký tài khoản thành công!");
                        } catch (Exception ex) {
                            response.setAction("REGISTER_FAILED");
                            response.setPayload("Tên đăng nhập đã tồn tại hoặc lỗi hệ thống.");
                        }
                    }
                }

                // ================= XỬ LÝ TẠO PHIÊN ĐẤU GIÁ =================
                else if ("CREATE_AUCTION".equals(request.getAction())) {
                    // PHÂN QUYỀN (RBAC): Chỉ người bán mới được tạo phiên
                    if (this.loggedInUser == null) {
                        response.setAction("CREATE_AUCTION_FAILED");
                        response.setPayload("Lỗi bảo mật: Bạn chưa đăng nhập!");
                    } else if (!"SELLER".equals(this.loggedInUser.getRole())) {
                        response.setAction("CREATE_AUCTION_FAILED");
                        response.setPayload("Lỗi bảo mật: Chỉ có người bán (SELLER) mới có quyền tạo phiên đấu giá!");
                    } else {
                        try {
                            Item newItem = gson.fromJson(request.getPayload(), Item.class);
                            // Mặc định tạo phiên đấu giá kéo dài 24 giờ
                            LocalDateTime endTime = LocalDateTime.now().plusHours(24);
                            AuctionService.getInstance().createNewAuction(newItem, endTime);

                            response.setAction("CREATE_AUCTION_SUCCESS");
                            response.setPayload("Tạo phiên đấu giá thành công!");
                        } catch (Exception e) {
                            response.setAction("CREATE_AUCTION_FAILED");
                            response.setPayload("Lỗi hệ thống khi tạo phiên: " + e.getMessage());
                        }
                    }
                }

                // ================= XỬ LÝ ĐẶT GIÁ (BIDDING) =================
                else if ("BID".equals(request.getAction())) {
                    // Yêu cầu phải đăng nhập mới được bid
                    if (this.loggedInUser == null) {
                        response.setAction("BID_FAILED");
                        response.setPayload("Lỗi: Bạn cần đăng nhập để tham gia đấu giá!");
                    } else {
                        String[] data = request.getPayload().split(":");
                        if (data.length == 3) {
                            try {
                                Long roomId = Long.parseLong(data[0]);
                                String username = data[1];
                                double amount = Double.parseDouble(data[2]);

                                User user = userDAO.findByUsername(username);
                                if (user != null) {
                                    Bidder bidder = new Bidder(user.getUserId(), user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getAccountBalance());

                                    // Gọi luồng xử lý lõi
                                    String result = AuctionService.getInstance().handleBidRequest(roomId, bidder, amount);

                                    if ("SUCCESS".equals(result)) {
                                        // 1. Trả lời riêng cho người đặt giá thành công
                                        response.setAction("BID_SUCCESS");
                                        response.setPayload("Đặt giá thành công!");
                                        out.println(gson.toJson(response));

                                        System.out.println("[Thành công] " + username + " đặt " + amount + " cho phòng " + roomId);

                                        // 2. OBSERVER: Phát thanh thông báo giá mới cho toàn bộ Client
                                        MessageDTO updateMsg = new MessageDTO();
                                        updateMsg.setAction("UPDATE_PRICE");
                                        updateMsg.setPayload(roomId + ":" + amount + ":" + username);
                                        broadcast(gson.toJson(updateMsg));

                                        continue; // Bỏ qua lệnh out.println ở cuối vòng lặp vì đã gửi rồi
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
                                response.setPayload("Dữ liệu đặt giá không hợp lệ.");
                            }
                        }
                    }
                }

                // ================= XỬ LÝ LỆNH KHÔNG HỢP LỆ =================
                else {
                    response.setAction("ERROR");
                    response.setPayload("Hành động không được hệ thống hỗ trợ.");
                }

                // Gửi response ngược lại Client cho các trường hợp chung
                String jsonResponse = gson.toJson(response);
                out.println(jsonResponse);
            }
        } catch (Exception e) {
            System.out.println(">>> Client ngắt kết nối: " + clientSocket.getInetAddress());
        } finally {
            // RẤT QUAN TRỌNG: Gỡ bỏ Client khỏi danh sách phát thanh khi họ tắt App
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