package server.application;

import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.AutoBidDAOImpl;
import server.dao.impl.BidMessageDAOImpl;
import server.dao.impl.ItemDAOImpl;
import server.dao.impl.UserDAOImpl;
import server.networks.ClientHandler;
import server.services.AuctionService;
import server.services.ItemService;
import server.services.UserService;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MainServer — Composition Root.
 *
 * Đây là nơi DUY NHẤT tạo các DAO và Service bằng `new`,
 * sau đó inject vào ClientHandler qua constructor.
 *
 * Mọi class khác (Service, Handler) nhận dependency qua constructor
 * → Dependency Inversion Principle (DIP) được đảm bảo toàn bộ hệ thống.
 */
public class MainServer {
    private static final int PORT = 8080;
    private static boolean isRunning = true;

    public static void main(String[] args) {
        // ── Wiring DAO ───────────────────────────────────────────────
        UserDAOImpl        userDAO    = new UserDAOImpl();
        ItemDAOImpl        itemDAO    = new ItemDAOImpl();
        AuctionRoomDAOImpl auctionDAO = new AuctionRoomDAOImpl();
        BidMessageDAOImpl  bidDAO     = new BidMessageDAOImpl();
        AutoBidDAOImpl     autoBidDAO = new AutoBidDAOImpl();

        // ── Wiring Service (Constructor Injection) ───────────────────
        UserService    userService    = new UserService(userDAO);
        ItemService    itemService    = new ItemService(itemDAO);
        AuctionService auctionService = AuctionService.getInstance(
                auctionDAO, itemDAO, bidDAO, userDAO, autoBidDAO);

        // ── Background scheduler ─────────────────────────────────────
        startBackgroundAuctionQuitter(auctionService);

        // ── Socket accept loop ───────────────────────────────────────
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(">>> [Hệ thống] Server AuctionVN đang chạy tại cổng: " + PORT);
            System.out.println(">>> [Hệ thống] Chế độ Virtual Threads: Đã kích hoạt.");

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                // Truyền dependencies vào ClientHandler qua constructor
                Thread.startVirtualThread(
                        new ClientHandler(clientSocket, userService, itemService, auctionService));
            }
        } catch (Exception e) {
            if (isRunning) {
                System.err.println(">>> [Lỗi] Server gặp sự cố: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void startBackgroundAuctionQuitter(AuctionService auctionService) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                auctionService.autoUpdateStatuses();
            } catch (Exception e) {
                System.err.println(">>> [Lỗi Quét] " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(">>> [Hệ thống] Đang dừng Server an toàn...");
            isRunning = false;
            scheduler.shutdown();
            server.dao.core.DBConnection.closePool();
        }));
    }
}