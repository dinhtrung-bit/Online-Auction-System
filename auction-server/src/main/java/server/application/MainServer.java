package server.application;

import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.AutoBidDAOImpl;
import server.dao.impl.BidMessageDAOImpl;
import server.dao.impl.DepositRequestDAOImpl;
import server.dao.impl.ItemDAOImpl;
import server.dao.impl.UserDAOImpl;
import server.dao.interfaces.DepositRequestDAO;
import server.networks.ClientHandler;
import server.networks.interfaces.BroadcastChannel;
import server.services.AuctionService;
import server.services.ItemService;
import server.services.UserService;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static server.networks.ClientHandler.activeClients;

public class MainServer {
    private static final int PORT = 8080;
    private static boolean isRunning = true;

    public static void main(String[] args) {
        UserDAOImpl userDAO = new UserDAOImpl();
        ItemDAOImpl itemDAO = new ItemDAOImpl();
        DepositRequestDAO depositDAO = new DepositRequestDAOImpl();

        AuctionRoomDAOImpl auctionDAO = new AuctionRoomDAOImpl(itemDAO, userDAO);
        BidMessageDAOImpl bidDAO = new BidMessageDAOImpl();
        AutoBidDAOImpl autoBidDAO = new AutoBidDAOImpl(userDAO);

        UserService userService = new UserService(userDAO, depositDAO);
        ItemService itemService = new ItemService(itemDAO);

        BroadcastChannel broadcaster = new BroadcastChannel() {
            @Override
            public void broadcast(String json) {
                for (ClientHandler client : activeClients) {
                    // TODO: nếu ClientHandler có hàm send/broadcast thì gọi ở đây
                }
            }
        };

        AuctionService auctionService = AuctionService.getInstance(
                auctionDAO,
                itemDAO,
                bidDAO,
                userDAO,
                autoBidDAO,
                broadcaster
        );

        startBackgroundAuctionQuitter(auctionService);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(">>> [Hệ thống] Server AuctionVN đang chạy tại cổng: " + PORT);
            System.out.println(">>> [Hệ thống] Chế độ Virtual Threads: Đã kích hoạt.");

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                Thread.startVirtualThread(
                        new ClientHandler(clientSocket, userService, itemService, auctionService)
                );
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