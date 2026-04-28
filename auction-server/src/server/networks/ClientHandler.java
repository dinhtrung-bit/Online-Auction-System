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

public class ClientHandler implements Runnable {
    private Socket clientSocket;    // Socket kết nối trực tiếp với 1 Client cụ thể
    private Gson gson;              // Đối tượng Gson để chuyển đổi JSON <-> Java Object
    private UserDAO userDAO;        // Tầng DAO tương tác với bảng users trong Database

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.gson = new Gson();               // Khởi tạo Gson
        this.userDAO = new UserDAOimpl();     // Khởi tạo DAO
    }

    @Override
    public void run() {
        try {
            System.out.println("Dang xu ly: " + clientSocket.getInetAddress());

            // 1. Khởi tạo luồng Đọc (in) và Ghi (out) dữ liệu qua Socket
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String inputLine;
            // 2. Vòng lặp while liên tục lắng nghe Client
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Server nhận yêu cầu: " + inputLine);

                // 3. Biến chuỗi JSON Client gửi thành đối tượng MessageDTO
                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);
                MessageDTO response = new MessageDTO();

                // =======================================================
                // 4. ĐIỀU HƯỚNG CÁC CHỨC NĂNG (ROUTING)
                // =======================================================

                // ---> CHỨC NĂNG ĐĂNG NHẬP
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
                    } else {
                        response.setAction("LOGIN_FAILED");
                        response.setPayload("Lỗi định dạng dữ liệu gửi lên.");
                    }
                }

                // ---> CHỨC NĂNG ĐĂNG KÝ
                else if ("REGISTER".equals(request.getAction())) {
                    String[] data = request.getPayload().split(":");

                    if (data.length == 3) {
                        String username = data[0];
                        String password = data[1];
                        String role = data[2];

                        try {
                            User newUser = UserFactory.createUser(role, 0, username);
                            // newUser.setPasswordHash(password); // Nhớ bỏ comment và set password ở đây

                            userDAO.insert(newUser);

                            response.setAction("REGISTER_SUCCESS");
                            response.setPayload("Đăng ký tài khoản thành công!");
                            System.out.println("[Thành công] Đã tạo user mới: " + username);
                        } catch (Exception ex) {
                            response.setAction("REGISTER_FAILED");
                            response.setPayload("Tên đăng nhập đã tồn tại hoặc lỗi hệ thống.");
                        }
                    } else {
                        response.setAction("REGISTER_FAILED");
                        response.setPayload("Thiếu thông tin đăng ký.");
                    }
                }

                // ---> CHỨC NĂNG ĐẶT GIÁ (BID)
                else if ("BID".equals(request.getAction())) {
                    // Quy ước Client gửi payload dạng: "roomId:username:amount"
                    String[] data = request.getPayload().split(":");

                    if (data.length == 3) {
                        try {
                            Long roomId = Long.parseLong(data[0]);
                            String username = data[1];
                            double amount = Double.parseDouble(data[2]);

                            // Tìm user từ DB
                            User user = userDAO.findByUsername(username);

                            if (user != null) {
                                // Khởi tạo Bidder từ dữ liệu User (Bạn có thể cần sửa dòng này cho khớp với Constructor của class Bidder thực tế)
                                Bidder bidder = new Bidder(user.getUserId(), user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getAccountBalance());

                                // Gọi Manager xử lý đặt giá
                                String result = AuctionService.getInstance().handleBidRequest(roomId, bidder, amount);

                                if ("SUCCESS".equals(result)) {
                                    response.setAction("BID_SUCCESS");
                                    response.setPayload("Đặt giá thành công!");
                                    System.out.println("[Thành công] User " + username + " đã đặt " + amount + " cho phòng " + roomId);
                                } else {
                                    response.setAction("BID_FAILED");
                                    response.setPayload(result); // VD: "Giá quá thấp"
                                }
                            } else {
                                response.setAction("BID_FAILED");
                                response.setPayload("Không tìm thấy thông tin người dùng.");
                            }
                        } catch (NumberFormatException e) {
                            response.setAction("BID_FAILED");
                            response.setPayload("Lỗi định dạng dữ liệu ID phòng hoặc Số tiền.");
                        }
                    } else {
                        response.setAction("BID_FAILED");
                        response.setPayload("Thiếu thông tin đặt giá. Yêu cầu: roomId:username:amount");
                    }
                }

                // ---> CHỨC NĂNG TẠO PHIÊN ĐẤU GIÁ (SELLER)
                else if ("CREATE_AUCTION".equals(request.getAction())) {
                    try {
                        // Nhận chuỗi JSON đại diện cho Item từ Client và ép kiểu
                        Item newItem = gson.fromJson(request.getPayload(), Item.class);

                        // Set thời gian kết thúc mặc định là 24h sau khi tạo
                        LocalDateTime endTime = LocalDateTime.now().plusHours(24);

                        // Lưu vào RAM và Database
                        AuctionService.getInstance().createNewAuction(newItem, endTime);

                        response.setAction("CREATE_AUCTION_SUCCESS");
                        response.setPayload("Tạo phiên đấu giá thành công!");
                        System.out.println("[Thành công] Đã tạo đấu giá cho sản phẩm: " + newItem.getName());

                    } catch (Exception e) {
                        response.setAction("CREATE_AUCTION_FAILED");
                        response.setPayload("Lỗi hệ thống khi tạo phiên đấu giá: " + e.getMessage());
                    }
                }

                // ---> CÁC HÀNH ĐỘNG KHÔNG XÁC ĐỊNH
                else {
                    response.setAction("ERROR");
                    response.setPayload("Hành động không được hỗ trợ.");
                }

                // 5. Đóng gói response thành JSON và gửi ngược lại về Client
                String jsonResponse = gson.toJson(response);
                out.println(jsonResponse);
            }
        } catch (Exception e) {
            System.out.println("Client đã ngắt kết nối: " + clientSocket.getInetAddress());
        } finally {
            // 6. Luôn nhớ đóng kết nối khi Client ngắt ngang để giải phóng RAM cho Server
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