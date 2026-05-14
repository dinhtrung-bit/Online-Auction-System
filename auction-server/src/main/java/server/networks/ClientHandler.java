package server.networks;

import com.google.gson.Gson;
import server.networks.dto.MessageDTO;
import server.networks.handlers.AuctionRequestHandler;
import server.networks.handlers.AutoBidRequestHandler;
import server.networks.handlers.ItemRequestHandler;
import server.networks.handlers.UserHolder;
import server.networks.handlers.UserRequestHandler;
import server.networks.interfaces.BroadcastChannel;
import server.services.AuctionService;
import server.services.ItemService;
import server.services.UserService;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ClientHandler — sau refactor chỉ còn 2 nhiệm vụ:
 *   1. Quản lý socket connection (read/write, cleanup)
 *   2. Route request đến đúng handler
 *
 * KHÔNG chứa business logic. KHÔNG gọi DAO trực tiếp.
 * Tuân thủ: Single Responsibility Principle + Layered Architecture.
 * Constructor Injection: nhận Service qua constructor thay vì tự new.
 * Fix 3.10: Loại bỏ từ khóa static.
 * Bây giờ mỗi ClientHandler có thể đóng vai trò là một kênh phát tin.
 */
public class ClientHandler implements Runnable  {

    public static final CopyOnWriteArrayList<ClientHandler> activeClients =
            new CopyOnWriteArrayList<>();

    private final Socket clientSocket;
    private final Gson gson = new Gson();
    private PrintWriter out;
    private final UserHolder userHolder = new UserHolder();

    private final UserRequestHandler    userHandler;
    private final ItemRequestHandler    itemHandler;
    private final AuctionRequestHandler auctionHandler;
    private final AutoBidRequestHandler autoBidHandler;
    private final UserService           userService;

    private final Map<String, RequestProcessor> router = new HashMap<>();

    public ClientHandler(Socket socket,
                         UserService userService,
                         ItemService itemService,
                         AuctionService auctionService) {
        this.clientSocket   = socket;
        this.userService    = userService;
        this.userHandler    = new UserRequestHandler(userService);
        this.itemHandler    = new ItemRequestHandler(itemService);
        this.auctionHandler = new AuctionRequestHandler(auctionService, itemService);
        this.autoBidHandler = new AutoBidRequestHandler(auctionService);
        activeClients.add(this);
        buildRouter();
    }

    private void buildRouter() {
        router.put("LOGIN",         req -> userHandler.handleLogin(req, userHolder));
        router.put("REGISTER",      req -> userHandler.handleRegister(req));
        router.put("GET_ALL_USERS", req -> userHandler.handleGetAllUsers(req, userHolder.getUser()));
        router.put("GET_BALANCE",                 req -> userHandler.handleGetBalance(req, userHolder));
        router.put("DEPOSIT",                     req -> userHandler.handleDeposit(req, userHolder));
        router.put("GET_MY_DEPOSIT_REQUESTS",     req -> userHandler.handleGetMyDepositRequests(req, userHolder));
        router.put("GET_DEPOSIT_REQUESTS",        req -> userHandler.handleGetDepositRequests(req, userHolder.getUser()));
        router.put("GET_PENDING_DEPOSITS",        req -> userHandler.handleGetPendingDeposits(req, userHolder.getUser()));
        router.put("APPROVE_DEPOSIT",             req -> userHandler.handleApproveDeposit(req, userHolder.getUser()));
        router.put("REJECT_DEPOSIT",              req -> userHandler.handleRejectDeposit(req, userHolder.getUser()));
        router.put("ADMIN_ADJUST_BALANCE",        req -> userHandler.handleAdminAdjustBalance(req, userHolder.getUser()));
        router.put("GET_DEPOSIT_STATS",           req -> userHandler.handleGetDepositStats(req, userHolder.getUser()));

        router.put("ADD_ITEM",     req -> itemHandler.handleAddItem(req,    userHolder.getUser()));
        router.put("UPDATE_ITEM",  req -> itemHandler.handleUpdateItem(req, userHolder.getUser()));
        router.put("DELETE_ITEM",  req -> itemHandler.handleDeleteItem(req, userHolder.getUser()));
        router.put("GET_MY_ITEMS", req -> itemHandler.handleGetMyItems(req, userHolder.getUser()));

        router.put("BID",                    req -> auctionHandler.handleBid(req, userHolder.getUser()));
        router.put("GET_AUCTION_DETAIL",     req -> auctionHandler.handleGetDetail(req));
        router.put("GET_AVAILABLE_AUCTIONS", req -> auctionHandler.handleGetAvailableAuctions(req));
        router.put("GET_ALL_AUCTIONS",       req -> auctionHandler.handleGetAllAuctions(req));
        router.put("GET_AUCTIONS_BY_STATUS", req -> auctionHandler.handleGetAuctionsByStatus(req));
        router.put("CREATE_AUCTION",         req -> auctionHandler.handleCreateAuction(req,      userHolder.getUser()));
        router.put("DELETE_AUCTION",         req -> auctionHandler.handleDeleteAuction(req,      userHolder.getUser()));
        router.put("ADMIN_CANCEL_AUCTION",   req -> auctionHandler.handleAdminCancelAuction(req, userHolder.getUser()));
        router.put("GET_MY_AUCTIONS",        req -> auctionHandler.handleGetMyAuctions(req,      userHolder.getUser()));
        router.put("GET_BID_HISTORY",        req -> auctionHandler.handleGetBidHistory(req,      userHolder.getUser()));
        router.put("GET_MY_WON_AUCTIONS",    req -> auctionHandler.handleGetMyWonAuctions(req,   userHolder.getUser()));
        router.put("GET_ADMIN_STATS",        req -> {
            try {
                int total = userService.findAll().size();
                int pendingDeposits = userService.countPendingDeposits();
                return auctionHandler.handleGetAdminStats(req, userHolder.getUser(), total, pendingDeposits);
            } catch (Exception e) {
                return new MessageDTO("ERROR", "Lỗi lấy thống kê: " + e.getMessage());
            }
        });
        router.put("GET_ADMIN_REVENUE_REPORT", req -> auctionHandler.handleGetAdminRevenueReport(req, userHolder.getUser()));

        router.put("SET_AUTO_BID",    req -> autoBidHandler.handleSetAutoBid(req,    userHolder.getUser()));
        router.put("CANCEL_AUTO_BID", req -> autoBidHandler.handleCancelAutoBid(req, userHolder.getUser()));
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()))) {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            String line;
            while ((line = in.readLine()) != null) {
                MessageDTO request  = gson.fromJson(line, MessageDTO.class);
                RequestProcessor rp = router.getOrDefault(request.getAction(),
                        req -> new MessageDTO("ERROR", "Hành động không hợp lệ: " + req.getAction()));
                MessageDTO response = rp.process(request);
                if (response != null) out.println(gson.toJson(response));
            }
        } catch (IOException e) {
            System.err.println(">>> Kết nối với Client bị ngắt.");
        } finally {
            cleanup();
        }
    }

    public void broadcast(String json) {
        activeClients.forEach(c -> { if (c.out != null) c.out.println(json); });
    }

    private void cleanup() {
        activeClients.remove(this);
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String json) {
        if (out != null) {
            out.println(json);
        }
    }

    @FunctionalInterface
    interface RequestProcessor {
        MessageDTO process(MessageDTO request);
    }
}