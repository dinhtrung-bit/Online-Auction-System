package server.application;

import server.services.AuctionService;
import server.networks.ClientHandler;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainServer {
    private static final int PORT = 8080;
    private static boolean isRunning = true;

    public static void main(String[] args) {
        // 1. Khởi tạo hệ thống quét tự động (Chạy định kỳ 1 giây)
        startBackgroundAuctionQuitter();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(">>> [Hệ thống] Server AuctionVN đang chạy tại cổng: " + PORT);
            System.out.println(">>> [Hệ thống] Chế độ Virtual Threads: Đã kích hoạt.");

            // 2. Vòng lặp lắng nghe kết nối
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();

                // TỐI ƯU: Sử dụng Virtual Thread để xử lý ClientHandler
                // Cực nhẹ, tốc độ xử lý nhanh và không làm nghẽn Server
                Thread.startVirtualThread(new ClientHandler(clientSocket));
            }
        } catch (Exception e) {
            if (isRunning) {
                System.err.println(">>> [Lỗi] Server gặp sự cố: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void startBackgroundAuctionQuitter() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Lập lịch quét trạng thái mỗi 1 giây
        scheduler.scheduleAtFixedRate(() -> {
            try {
                AuctionService.getInstance().autoUpdateStatuses();
            } catch (Exception e) {
                System.err.println(">>> [Lỗi Quét] " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);

        // Đảm bảo đóng scheduler sạch sẽ khi tắt Server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(">>> [Hệ thống] Đang dừng Server an toàn...");
            isRunning = false;
            scheduler.shutdown();
        }));
    }
}