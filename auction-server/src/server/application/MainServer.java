package server.application;

import server.services.AuctionService;
import server.networks.ClientHandler;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainServer {
    public static void main(String[] args) {
        int port = 8080;

        // 1. Khởi tạo luồng chạy ngầm để quét và đóng các phiên đấu giá hết hạn
        startBackgroundAuctionQuitter();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println(">>> Server đang chạy tại cổng: " + port);
            System.out.println(">>> Hệ thống quét phiên đấu giá tự động đã kích hoạt.");

            // 2. Vòng lặp chính lắng nghe các kết nối từ Client
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Mỗi Client kết nối sẽ được xử lý bởi một luồng riêng (ClientHandler)
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (Exception e) {
            System.err.println("Lỗi Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Phương thức khởi tạo một luồng chạy định kỳ mỗi 1 giây
     * để gọi hàm autoUpdateStatuses trong AuctionManager.
     */
    private static void startBackgroundAuctionQuitter() {
        // Tạo một scheduler với 1 luồng duy nhất chuyên trách việc quét thời gian
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Lập lịch thực hiện tác vụ:
        // - Lần đầu tiên chạy sau 0 giây (ngay lập tức)
        // - Các lần tiếp theo cách nhau đúng 1 giây
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Gọi "bộ não" AuctionManager để kiểm tra trạng thái các phòng
                AuctionService.getInstance().autoUpdateStatuses();
            } catch (Exception e) {
                System.err.println("Lỗi trong quá trình quét tự động: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);

        // Đảm bảo scheduler sẽ tắt khi Server ngừng hoạt động (tùy chọn)
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
    }
}