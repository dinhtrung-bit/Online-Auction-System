package server.networks;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import server.networks.dto.MessageDTO;
import server.networks.handlers.AuctionRequestHandler;
import server.networks.handlers.AutoBidRequestHandler;
import server.networks.handlers.ItemRequestHandler;
import server.networks.handlers.UserHolder;
import server.networks.handlers.UserRequestHandler;
import server.services.AuctionService;
import server.services.ItemService;
import server.services.UserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {

    public static final CopyOnWriteArrayList<ClientHandler> activeClients =
            new CopyOnWriteArrayList<>();

    private final Socket clientSocket;
    private final Gson gson = new Gson();

    private PrintWriter out;

    private final UserHolder userHolder = new UserHolder();

    private final UserRequestHandler userHandler;
    private final ItemRequestHandler itemHandler;
    private final AuctionRequestHandler auctionHandler;
    private final AutoBidRequestHandler autoBidHandler;

    private final UserService userService;

    private final Map<String, RequestProcessor> router = new HashMap<>();

    public ClientHandler(
            Socket socket,
            UserService userService,
            ItemService itemService,
            AuctionService auctionService
    ) {
        this.clientSocket = socket;
        this.userService = userService;

        this.userHandler = new UserRequestHandler(userService);
        this.itemHandler = new ItemRequestHandler(itemService);
        this.auctionHandler = new AuctionRequestHandler(auctionService, itemService);
        this.autoBidHandler = new AutoBidRequestHandler(auctionService);

        activeClients.add(this);

        buildRouter();
    }

    private void buildRouter() {

        // USER
        router.put("LOGIN", req -> userHandler.handleLogin(req, userHolder));
        router.put("REGISTER", req -> userHandler.handleRegister(req));
        router.put("GET_ALL_USERS", req -> userHandler.handleGetAllUsers(req, userHolder.getUser()));
        router.put("GET_BALANCE", req -> userHandler.handleGetBalance(req, userHolder));
        router.put("DEPOSIT", req -> userHandler.handleDeposit(req, userHolder));
        router.put("GET_MY_DEPOSIT_REQUESTS", req -> userHandler.handleGetMyDepositRequests(req, userHolder));
        router.put("GET_DEPOSIT_REQUESTS", req -> userHandler.handleGetDepositRequests(req, userHolder.getUser()));
        router.put("GET_PENDING_DEPOSITS", req -> userHandler.handleGetPendingDeposits(req, userHolder.getUser()));
        router.put("APPROVE_DEPOSIT", req -> userHandler.handleApproveDeposit(req, userHolder.getUser()));
        router.put("REJECT_DEPOSIT", req -> userHandler.handleRejectDeposit(req, userHolder.getUser()));
        router.put("ADMIN_ADJUST_BALANCE", req -> userHandler.handleAdminAdjustBalance(req, userHolder.getUser()));
        router.put("GET_DEPOSIT_STATS", req -> userHandler.handleGetDepositStats(req, userHolder.getUser()));

        // ITEM
        router.put("ADD_ITEM", req -> itemHandler.handleAddItem(req, userHolder.getUser()));
        router.put("UPDATE_ITEM", req -> itemHandler.handleUpdateItem(req, userHolder.getUser()));
        router.put("DELETE_ITEM", req -> itemHandler.handleDeleteItem(req, userHolder.getUser()));
        router.put("GET_MY_ITEMS", req -> itemHandler.handleGetMyItems(req, userHolder.getUser()));

        // AUCTION
        router.put("BID", req -> auctionHandler.handleBid(req, userHolder.getUser()));
        router.put("GET_AUCTION_DETAIL", req -> auctionHandler.handleGetDetail(req));
        router.put("GET_AVAILABLE_AUCTIONS", req -> auctionHandler.handleGetAvailableAuctions(req));
        router.put("GET_ALL_AUCTIONS", req -> auctionHandler.handleGetAllAuctions(req));
        router.put("GET_AUCTIONS_BY_STATUS", req -> auctionHandler.handleGetAuctionsByStatus(req));

        router.put("CREATE_AUCTION",
                req -> auctionHandler.handleCreateAuction(req, userHolder.getUser()));

        router.put("DELETE_AUCTION",
                req -> auctionHandler.handleDeleteAuction(req, userHolder.getUser()));

        router.put("ADMIN_CANCEL_AUCTION",
                req -> auctionHandler.handleAdminCancelAuction(req, userHolder.getUser()));

        router.put("GET_MY_AUCTIONS",
                req -> auctionHandler.handleGetMyAuctions(req, userHolder.getUser()));

        router.put("GET_BID_HISTORY",
                req -> auctionHandler.handleGetBidHistory(req, userHolder.getUser()));

        router.put("GET_MY_WON_AUCTIONS",
                req -> auctionHandler.handleGetMyWonAuctions(req, userHolder.getUser()));

        router.put("GET_ADMIN_STATS", req -> {
            try {
                int totalUsers = userService.findAll().size();
                int pendingDeposits = userService.countPendingDeposits();

                return auctionHandler.handleGetAdminStats(
                        req,
                        userHolder.getUser(),
                        totalUsers,
                        pendingDeposits
                );

            } catch (Exception e) {
                return new MessageDTO("ERROR", "Lỗi lấy thống kê: " + e.getMessage());
            }
        });

        router.put("GET_ADMIN_REVENUE_REPORT",
                req -> auctionHandler.handleGetAdminRevenueReport(req, userHolder.getUser()));

        // AUTO BID
        router.put("SET_AUTO_BID",
                req -> autoBidHandler.handleSetAutoBid(req, userHolder.getUser()));

        router.put("CANCEL_AUTO_BID",
                req -> autoBidHandler.handleCancelAutoBid(req, userHolder.getUser()));
    }

    @Override
    public void run() {

        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream())
                )
        ) {

            out = new PrintWriter(clientSocket.getOutputStream(), true);

            System.out.println(">>> [Client Connected] " +
                    clientSocket.getInetAddress());

            String line;

            while ((line = in.readLine()) != null) {

                MessageDTO request;

                try {
                    request = gson.fromJson(line, MessageDTO.class);

                } catch (JsonSyntaxException e) {

                    sendError("JSON không hợp lệ.");
                    continue;
                }

                if (request == null) {
                    sendError("Request không hợp lệ.");
                    continue;
                }

                if (request.getAction() == null ||
                        request.getAction().trim().isEmpty()) {

                    sendError("Thiếu action.");
                    continue;
                }

                RequestProcessor processor = router.get(request.getAction());

                if (processor == null) {

                    sendError("Action không hợp lệ: " + request.getAction());
                    continue;
                }

                try {

                    MessageDTO response = processor.process(request);

                    if (response != null) {
                        send(response);
                    }

                } catch (Exception e) {

                    System.err.println(">>> [Handler Error] " + e.getMessage());

                    sendError("Lỗi xử lý request: " + e.getMessage());
                }
            }

        } catch (IOException e) {

            System.out.println(">>> [Disconnected] Client đã ngắt kết nối.");

        } finally {

            cleanup();
        }
    }

    public void broadcast(String json) {

        for (ClientHandler client : activeClients) {

            try {

                if (client.out != null) {
                    client.out.println(json);
                }

            } catch (Exception ignored) {
            }
        }
    }

    public void sendMessage(String json) {

        try {

            if (out != null) {
                out.println(json);
            }

        } catch (Exception ignored) {
        }
    }

    private void send(MessageDTO dto) {

        if (dto == null || out == null) return;

        out.println(gson.toJson(dto));
    }

    private void sendError(String message) {

        send(new MessageDTO("ERROR", message));
    }

    private void cleanup() {

        activeClients.remove(this);

        try {

            if (out != null) {
                out.close();
            }

        } catch (Exception ignored) {
        }

        try {

            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

        } catch (IOException e) {

            System.err.println(">>> [Cleanup Error] " + e.getMessage());
        }

        System.out.println(">>> [Client Cleanup] Đã giải phóng kết nối.");
    }

    @FunctionalInterface
    interface RequestProcessor {

        MessageDTO process(MessageDTO request);
    }
}