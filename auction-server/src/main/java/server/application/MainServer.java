package server.application;

import static server.networks.ClientHandler.activeClients;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import server.dao.core.DBConnection;
import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.AutoBidDAOImpl;
import server.dao.impl.BidMessageDAOImpl;
import server.dao.impl.ItemDAOImpl;
import server.dao.impl.UserDAOImpl;
import server.networks.ClientHandler;
import server.networks.interfaces.BroadcastChannel;
import server.services.AuctionService;
import server.services.ItemService;
import server.services.UserService;

/** Điểm khởi động chính của server: tạo các DAO, Service và mở ServerSocket. */
public class MainServer {

    private static final int PORT = 8080;
    private static final int AUCTION_TICK_SECONDS = 1;

    private static volatile boolean running = true;

    private MainServer() {
        // tránh khởi tạo
    }

    public static void main(String[] args) {
        UserDAOImpl userDAO = new UserDAOImpl();
        ItemDAOImpl itemDAO = new ItemDAOImpl();
        AuctionRoomDAOImpl auctionDAO = new AuctionRoomDAOImpl(itemDAO, userDAO);
        BidMessageDAOImpl bidDAO = new BidMessageDAOImpl();
        AutoBidDAOImpl autoBidDAO = new AutoBidDAOImpl(userDAO);

        UserService userService = new UserService(userDAO);
        ItemService itemService = new ItemService(itemDAO);

        BroadcastChannel broadcaster = json -> {
            for (ClientHandler client : activeClients) {
                client.sendMessage(json);
            }
        };

        AuctionService auctionService = AuctionService.getInstance(
                auctionDAO, itemDAO, bidDAO, userDAO, autoBidDAO, broadcaster);

        startAuctionStatusScheduler(auctionService);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(">>> [Hệ thống] Server AuctionVN đang chạy tại cổng: " + PORT);
            System.out.println(">>> [Hệ thống] Chế độ Virtual Threads: Đã kích hoạt.");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                Thread.startVirtualThread(
                        new ClientHandler(clientSocket, userService, itemService, auctionService));
            }
        } catch (Exception e) {
            if (running) {
                System.err.println(">>> [Lỗi] Server gặp sự cố: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void startAuctionStatusScheduler(AuctionService auctionService) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                auctionService.autoUpdateStatuses();
            } catch (Exception e) {
                System.err.println(">>> [Lỗi Quét] " + e.getMessage());
            }
        }, 0, AUCTION_TICK_SECONDS, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(">>> [Hệ thống] Đang dừng Server an toàn...");
            running = false;
            scheduler.shutdown();
            DBConnection.closePool();
        }));
    }
}