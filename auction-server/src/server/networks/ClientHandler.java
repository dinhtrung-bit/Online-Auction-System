package server.networks;

import com.google.gson.Gson;
import server.DAO.UserDAO;
import server.DAO.UserDAOimpl;
import server.models.User;
import server.models.UserFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

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
            // InputStreamReader đọc byte từ Socket, BufferedReader giúp đọc từng dòng text (JSON)
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            // PrintWriter đẩy text về Client. Tham số 'true' giúp tự động đẩy (flush) ngay lập tức
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String inputLine;
            // 2. Vòng lặp while liên tục lắng nghe Client. Hàm readLine() sẽ block cho đến khi có tin nhắn mới
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Server nhận yêu cầu: " + inputLine);

                // 3. Biến chuỗi JSON Client gửi thành đối tượng MessageDTO
                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);

                // Khởi tạo đối tượng rỗng để chuẩn bị gửi phản hồi về Client
                MessageDTO response = new MessageDTO();

                // =======================================================
                // 4. ĐIỀU HƯỚNG CÁC CHỨC NĂNG (ROUTING)
                // =======================================================

                // ---> CHỨC NĂNG ĐĂNG NHẬP
                if ("LOGIN".equals(request.getAction())) {
                    // Giả sử Client gửi lên payload dạng: "username:password"
                    String[] credentials = request.getPayload().split(":");

                    if (credentials.length == 2) {
                        String username = credentials[0];
                        String password = credentials[1];

                        // Gọi DAO tìm kiếm user dưới CSDL
                        User user = userDAO.findByUsername(username);

                        // Kiểm tra: Nếu user tồn tại và password khớp với CSDL
                        if (user != null && password.equals(user.getPasswordHash())) {
                            response.setAction("LOGIN_SUCCESS");
                            // Nén toàn bộ thông tin User thành JSON trả về cho giao diện Client
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
                    // Giả sử payload đăng ký là: "username:password:role"
                    String[] data = request.getPayload().split(":");

                    if (data.length == 3) {
                        String username = data[0];
                        String password = data[1];
                        String role = data[2];

                        try {
                            // Dùng Factory của Thành viên 1 để tạo đối tượng User mới
                            User newUser = UserFactory.createUser(role, 0, username);
                            // Gắn password cho user (Xem lưu ý số 2 bên dưới)
                            // newUser.setPasswordHash(password);

                            // Đẩy xuống CSDL thông qua hàm insert đã viết ở DAO
                            userDAO.insert(newUser);

                            response.setAction("REGISTER_SUCCESS");
                            response.setPayload("Đăng ký tài khoản thành công!");
                            System.out.println("[Thành công] Đã tạo user mới: " + username);
                        } catch (Exception ex) {
                            // Bắt lỗi Exception từ DB (ví dụ: trùng username UNIQUE)
                            response.setAction("REGISTER_FAILED");
                            response.setPayload("Tên đăng nhập đã tồn tại hoặc lỗi hệ thống.");
                        }
                    } else {
                        response.setAction("REGISTER_FAILED");
                        response.setPayload("Thiếu thông tin đăng ký.");
                    }
                }
                // Các chức năng khác như BID, CREATE_AUCTION sẽ được thêm bằng if-else (hoặc switch-case) ở đây
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