package server.networks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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

/**
 * ClientHandler — Một instance phục vụ một kết nối TCP.
 *
 * <p>Nhiệm vụ:
 *
 * <ul>
 *   <li>Đọc dòng JSON từ socket, parse thành {@link MessageDTO}.
 *   <li>Tra cứu {@link RequestProcessor} qua bảng router theo {@code action}.
 *   <li>Trả response, broadcast tới các client khác khi cần.
 * </ul>
 */
public class ClientHandler implements Runnable {

    /** Danh sách tất cả ClientHandler đang hoạt động — dùng để broadcast. */
    public static final CopyOnWriteArrayList<ClientHandler> activeClients =
            new CopyOnWriteArrayList<>();

    private final Socket clientSocket;
    private final Gson gson = new Gson();
    private final UserHolder userHolder = new UserHolder();
    private final Map<String, RequestProcessor> router = new HashMap<>();

    private final UserRequestHandler userHandler;
    private final ItemRequestHandler itemHandler;
    private final AuctionRequestHandler auctionHandler;
    private final AutoBidRequestHandler autoBidHandler;

    private final UserService userService;

    private PrintWriter out;

    public ClientHandler(
            Socket socket,
            UserService userService,
            ItemService itemService,
            AuctionService auctionService) {
        this.clientSocket = socket;
        this.userService = userService;
        this.userHandler = new UserRequestHandler(userService);
        this.itemHandler = new ItemRequestHandler(itemService);
        this.auctionHandler = new AuctionRequestHandler(auctionService, itemService);
        this.autoBidHandler = new AutoBidRequestHandler(auctionService);

        activeClients.add(this);
        buildRouter();
    }

    // ─── Router setup ─────────────────────────────────────────────────────────

    private void buildRouter() {
        // USER
        router.put("LOGIN",                req -> userHandler.handleLogin(req, userHolder));
        router.put("REGISTER",             req -> userHandler.handleRegister(req));
        router.put("GET_ALL_USERS",        req -> userHandler.handleGetAllUsers(req, userHolder.getUser()));
        router.put("GET_BALANCE",          req -> userHandler.handleGetBalance(req, userHolder));
        router.put("ADMIN_ADJUST_BALANCE", req -> userHandler.handleAdminAdjustBalance(req, userHolder.getUser()));
        router.put("DEPOSIT",              req -> userHandler.handleSelfDeposit(req, userHolder));

        // ITEM
        router.put("ADD_ITEM",     req -> itemHandler.handleAddItem(req, userHolder.getUser()));
        router.put("UPDATE_ITEM",  req -> itemHandler.handleUpdateItem(req, userHolder.getUser()));
        router.put("DELETE_ITEM",  req -> itemHandler.handleDeleteItem(req, userHolder.getUser()));
        router.put("GET_MY_ITEMS", req -> itemHandler.handleGetMyItems(req, userHolder.getUser()));

        // AUCTION
        router.put("BID",                    req -> auctionHandler.handleBid(req, userHolder.getUser()));
        router.put("GET_AUCTION_DETAIL",     req -> auctionHandler.handleGetDetail(req));
        router.put("GET_AVAILABLE_AUCTIONS", req -> auctionHandler.handleGetAvailableAuctions(req));
        router.put("GET_ALL_AUCTIONS",       req -> auctionHandler.handleGetAllAuctions(req));
        router.put("GET_AUCTIONS_BY_STATUS", req -> auctionHandler.handleGetAuctionsByStatus(req));
        router.put("CREATE_AUCTION",         req -> auctionHandler.handleCreateAuction(req, userHolder.getUser()));
        router.put("DELETE_AUCTION",         req -> auctionHandler.handleDeleteAuction(req, userHolder.getUser()));
        router.put("ADMIN_CANCEL_AUCTION",   req -> auctionHandler.handleAdminCancelAuction(req, userHolder.getUser()));
        router.put("GET_MY_AUCTIONS",        req -> auctionHandler.handleGetMyAuctions(req, userHolder.getUser()));
        router.put("GET_BID_HISTORY",        req -> auctionHandler.handleGetBidHistory(req, userHolder.getUser()));
        router.put("GET_MY_WON_AUCTIONS",    req -> auctionHandler.handleGetMyWonAuctions(req, userHolder.getUser()));

        router.put("GET_ADMIN_STATS", req -> {
            try {
                int totalUsers = userService.findAll().size();
                return auctionHandler.handleGetAdminStats(req, userHolder.getUser(), totalUsers);
            } catch (Exception e) {
                return new MessageDTO("ERROR", "Lỗi lấy thống kê: " + e.getMessage());
            }
        });

        // AUTO BID
        router.put("SET_AUTO_BID",    req -> autoBidHandler.handleSetAutoBid(req, userHolder.getUser()));
        router.put("CANCEL_AUTO_BID", req -> autoBidHandler.handleCancelAutoBid(req, userHolder.getUser()));
    }

    // ─── Main loop ───────────────────────────────────────────────────────────

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()))) {

            out = new PrintWriter(clientSocket.getOutputStream(), true);
            System.out.println(">>> [Client Connected] " + clientSocket.getInetAddress());

            String line;
            while ((line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            System.out.println(">>> [Disconnected] Client đã ngắt kết nối.");
        } finally {
            cleanup();
        }
    }

    private void handleLine(String line) {
        MessageDTO request;
        try {
            request = gson.fromJson(line, MessageDTO.class);
        } catch (JsonSyntaxException e) {
            sendError("JSON không hợp lệ.");
            return;
        }

        if (request == null) {
            sendError("Request không hợp lệ.");
            return;
        }

        if (request.getAction() == null || request.getAction().trim().isEmpty()) {
            sendError("Thiếu action.");
            return;
        }

        RequestProcessor processor = router.get(request.getAction());
        if (processor == null) {
            sendError("Action không hợp lệ: " + request.getAction());
            return;
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

    // ─── Network I/O ─────────────────────────────────────────────────────────

    /** Gửi tin nhắn JSON tới riêng client của connection này. */
    public void sendMessage(String json) {
        if (out == null) {
            return;
        }
        try {
            out.println(json);
        } catch (Exception ignored) {
            // socket có thể đã đóng — bỏ qua
        }
    }

    private void send(MessageDTO dto) {
        if (dto == null || out == null) {
            return;
        }
        out.println(gson.toJson(dto));
    }

    private void sendError(String message) {
        send(new MessageDTO("ERROR", message));
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    private void cleanup() {
        activeClients.remove(this);

        if (out != null) {
            try {
                out.close();
            } catch (Exception ignored) {
                // bỏ qua
            }
        }

        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println(">>> [Cleanup Error] " + e.getMessage());
            }
        }

        System.out.println(">>> [Client Cleanup] Đã giải phóng kết nối.");
    }

    @FunctionalInterface
    interface RequestProcessor {
        MessageDTO process(MessageDTO request);
    }
}