package server.networks;

import com.google.gson.Gson;
import server.DAO.UserDAOimpl;
import server.models.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private Gson gson = new Gson();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            // Khởi tạo luồng Đọc/Ghi dữ liệu với Client
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String inputLine;
            // Vòng lặp vô tận để giữ kết nối và liên tục lắng nghe Client
            while ((inputLine = in.readLine()) != null) {
                System.out.println("[Server] Nhận được dữ liệu: " + inputLine);

                // Chuyển chuỗi JSON nhận được thành đối tượng MessageDTO
                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);

                // ==========================================
                // XỬ LÝ CÁC YÊU CẦU TỪ CLIENT Ở ĐÂY
                // ==========================================
                if ("LOGIN".equals(request.getAction())) {
                    handleLogin(request.getPayload());
                }
                else if ("BID".equals(request.getAction())) {
                    // TODO: Xử lý chức năng đấu giá sau này
                }
            }
        } catch (IOException e) {
            System.out.println("[Server] Client đã ngắt kết nối: " + clientSocket.getInetAddress());
        } finally {
            closeConnections();
        }
    }

    /**
     * Hàm xử lý logic đăng nhập
     */
    private void handleLogin(String payload) {
        try {
            // Tách username và password từ payload (Cấu trúc: "username|password")
            String[] credentials = payload.split("\\|");
            if (credentials.length < 2) {
                sendResponse("LOGIN_FAILURE", "Dữ liệu gửi lên không hợp lệ");
                return;
            }

            String username = credentials[0];
            String password = credentials[1];

            // Gọi DAO để truy vấn Database
            UserDAOimpl userDAO = new UserDAOimpl();
            User user = userDAO.login(username, password); // Đảm bảo bạn có hàm login(user, pass) trong UserDAOimpl

            // Kiểm tra kết quả và phản hồi lại Client
            if (user != null) {
                sendResponse("LOGIN_SUCCESS", "Đăng nhập thành công! Chào " + user.getUsername());
            } else {
                sendResponse("LOGIN_FAILURE", "Sai tài khoản hoặc mật khẩu!");
            }

        } catch (Exception e) {
            System.err.println("[Server] Lỗi xử lý đăng nhập: " + e.getMessage());
            sendResponse("LOGIN_FAILURE", "Lỗi hệ thống máy chủ!");
        }
    }

    /**
     * Hàm hỗ trợ đóng gói và gửi phản hồi dạng JSON
     */
    private void sendResponse(String action, String payload) {
        MessageDTO response = new MessageDTO(action, payload);
        String jsonResponse = gson.toJson(response);
        out.println(jsonResponse);
        System.out.println("[Server] Đã gửi phản hồi: " + jsonResponse);
    }

    /**
     * Dọn dẹp tài nguyên khi Client thoát
     */
    private void closeConnections() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}