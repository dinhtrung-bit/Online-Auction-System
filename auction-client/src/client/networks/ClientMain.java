package client.networks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientMain {
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;

    // Gọi hàm này 1 lần duy nhất lúc khởi động app JavaFX
    public static void connectToServer() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket("localhost", 8080);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                System.out.println("Đã kết nối thành công tới Server!");
            }
        } catch (Exception e) {
            System.err.println("Lỗi kết nối tới Server: " + e.getMessage());
        }
    }

    public static void send(String jsonString) {
        if (out != null) {
            out.println(jsonString);
            System.out.println("Đã gửi: " + jsonString);
        }
    }

    public static String receive() throws Exception {
        if (in != null) {
            return in.readLine();
        }
        return null;
    }
}