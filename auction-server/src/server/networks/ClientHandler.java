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

/**
 * ClientHandler xử lý yêu cầu từ Client cho hệ thống AuctionVN.
 */
public class ClientHandler implements Runnable {

    private static final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();
    private Socket clientSocket;
    private Gson gson;
    private UserDAO userDAO;
    private PrintWriter out;
    private User loggedInUser = null;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.gson = new Gson();
        this.userDAO = new UserDAOimpl();
        activeClients.add(this);
    }

    private static void broadcast(String jsonMessage) {
        for (ClientHandler client : activeClients) {
            try {
                if (client.out != null) {
                    client.out.println(jsonMessage);
                }
            } catch (Exception e) {
                System.err.println("Lỗi broadcast: " + e.getMessage());
            }
        }
    }

    @Override
    public void run() {
        try {
            System.out.println(">>> Đã kết nối với: " + clientSocket.getInetAddress());
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);
                MessageDTO response = new MessageDTO();

                // ================= 1. XỬ LÝ ĐĂNG NHẬP =================
                if ("LOGIN".equals(request.getAction())) {
                    String[] credentials = request.getPayload().split(":");

                    if (credentials.length == 3) {
                        String selectedRole = credentials[0];
                        String username = credentials[1];
                        String password = credentials[2]; // Lấy nguyên bản mật khẩu, không dùng .trim()

                        try {
                            User user = userDAO.findByUsername(username);

                            if (user != null) {
                                // ====== THÊM 2 DÒNG NÀY VÀO ĐỂ DEBUG ======
                                System.out.println("-> Pass Client gửi lên : [" + password + "] (Độ dài: " + password.length() + ")");
                                System.out.println("-> Pass trong Database : [" + user.getPasswordHash() + "] (Độ dài: " + user.getPasswordHash().length() + ")");
                                // ==========================================

                                // So sánh trực tiếp: Nếu nhập thừa dấu cách sẽ trả về false ngay
                                boolean isPassCorrect = password.equals(user.getPasswordHash());
                                boolean isRoleCorrect = user.getRole().equalsIgnoreCase(selectedRole);

                                if (isPassCorrect && isRoleCorrect) {
                                    this.loggedInUser = user;
                                    response.setAction("LOGIN_SUCCESS");
                                    response.setPayload(gson.toJson(user));
                                    System.out.println("[OK] Đăng nhập: " + username);
                                } else {
                                    response.setAction("LOGIN_FAILED");
                                    // Chỉ hiện thông báo ngắn gọn
                                    response.setPayload(!isPassCorrect ? "Sai mật khẩu!" : "Sai vai trò!");
                                }
                            } else {
                                response.setAction("LOGIN_FAILED");
                                response.setPayload("Tên đăng nhập không tồn tại!");
                            }
                        } catch (Exception e) {
                            response.setAction("LOGIN_FAILED");
                            response.setPayload("Lỗi kết nối cơ sở dữ liệu!");
                        }
                    }
                }

                // ================= 2. XỬ LÝ ĐĂNG KÝ =================
                else if ("REGISTER".equals(request.getAction())) {
                    String[] data = request.getPayload().split(":");
                    if (data.length == 3) {
                        try {
                            User newUser = UserFactory.createUser(data[2], 0, data[0]);
                            newUser.setPasswordHash(data[1]);
                            userDAO.insert(newUser);
                            response.setAction("REGISTER_SUCCESS");
                            response.setPayload("Đăng ký thành công!");
                        } catch (Exception ex) {
                            response.setAction("REGISTER_FAILED");
                            response.setPayload("Tài khoản đã tồn tại.");
                        }
                    }
                }

                // ================= 3. XỬ LÝ ĐẶT GIÁ =================
                else if ("BID".equals(request.getAction())) {
                    if (this.loggedInUser != null) {
                        String[] data = request.getPayload().split(":");
                        if (data.length == 3) {
                            try {
                                Long roomId = Long.parseLong(data[0]);
                                String userBid = data[1];
                                double amount = Double.parseDouble(data[2]);

                                User user = userDAO.findByUsername(userBid);
                                if (user != null) {
                                    Bidder bidder = new Bidder(user.getUserId(), user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getAccountBalance());
                                    String result = AuctionService.getInstance().handleBidRequest(roomId, bidder, amount);

                                    if ("SUCCESS".equals(result)) {
                                        response.setAction("BID_SUCCESS");
                                        response.setPayload("Đặt giá thành công!");
                                        out.println(gson.toJson(response));

                                        MessageDTO updateMsg = new MessageDTO();
                                        updateMsg.setAction("UPDATE_PRICE");
                                        updateMsg.setPayload(roomId + ":" + amount + ":" + userBid);
                                        broadcast(gson.toJson(updateMsg));
                                        continue;
                                    } else {
                                        response.setAction("BID_FAILED");
                                        response.setPayload(result);
                                    }
                                }
                            } catch (Exception e) {
                                response.setAction("BID_FAILED");
                                response.setPayload("Lỗi dữ liệu.");
                            }
                        }
                    }
                }

                out.println(gson.toJson(response));
            }
        } catch (Exception e) {
            System.err.println("Client ngắt kết nối.");
        } finally {
            activeClients.remove(this);
            try {
                if (clientSocket != null) clientSocket.close();
            } catch (Exception ex) { }
        }
    }
}