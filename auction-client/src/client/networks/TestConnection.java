//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.io.PrintWriter;
//import java.net.Socket;
//
//void main() {
//    String host = "localhost";
//    int port = 8080;
//
//    System.out.println("=== BẮT ĐẦU KIỂM TRA KẾT NỐI 3 THÀNH PHẦN ===");
//    System.out.println("Lưu ý: Đảm bảo ServerRocket đã hiện chữ 'running' nhé!");
//    System.out.println("----------------------------------------------");
//
//    try (Socket socket = new Socket(host, port);
//         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
//
//        System.out.println("[1] Kết nối Socket tới Server: THÀNH CÔNG");
//
//        // Giả lập giao diện gửi gói tin Đăng nhập
//        // Thay "admin|123456" bằng tài khoản/mật khẩu thật có trong Database của bạn
//        String jsonRequest = "{\"action\":\"LOGIN\",\"payload\":\"admin|123456\"}";
//
//        System.out.println("[2] Gửi yêu cầu qua mạng: " + jsonRequest);
//        out.println(jsonRequest);
//
//        System.out.println("[3] Đang chờ Server truy vấn Database...");
//
//        // Đọc phản hồi trả về từ Server
//        String response = in.readLine();
//        System.out.println("[4] Nhận được phản hồi: " + response);
//
//        System.out.println("----------------------------------------------");
//        if (response != null && response.contains("LOGIN_SUCCESS")) {
//            System.out.println("🎉 KẾT LUẬN: TUYỆT VỜI! MẠCH KẾT NỐI CLIENT -> SERVER -> DATABASE ĐÃ THÔNG SUỐT!");
//        } else {
//            System.out.println("⚠️ KẾT LUẬN: Đã tới được Database, nhưng sai tài khoản/mật khẩu, hoặc chưa có user này trong DB.");
//        }
//
//    } catch (Exception e) {
//        System.err.println("❌ KẾT LUẬN: THẤT BẠI. Không tìm thấy Server. Hãy kiểm tra lại Bước 2 (ServerRocket đã chạy chưa?).");
//        e.printStackTrace();
//    }
//}