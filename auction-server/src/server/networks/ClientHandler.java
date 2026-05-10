package server.networks;

import com.google.gson.Gson;
import server.models.users.Seller;
import server.networks.dto.MessageDTO;
import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.AutoBidDAOImpl;
import server.dao.impl.BidMessageDAOImpl;
import server.dao.impl.ItemDAOImpl;
import server.dao.impl.UserDAOImpl;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.AutoBidDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.AutoBidConfig;
import server.models.auction.BidMessage;
import server.models.items.Item;
import server.models.items.ItemFactory;
import server.models.users.Bidder;
import server.models.users.User;
import server.models.users.UserFactory;
import server.services.AuctionService;
import server.services.PasswordUtil;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {
    private static final CopyOnWriteArrayList<ClientHandler> activeClients = new CopyOnWriteArrayList<>();
    private final Socket clientSocket;
    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAOImpl();
    private final ItemDAO itemDAO = new ItemDAOImpl();
    private final AuctionRoomDAO auctionDAO = new AuctionRoomDAOImpl();
    private final BidMessageDAO bidMessageDAO = new BidMessageDAOImpl();
    private final AutoBidDAO autoBidDAO = new AutoBidDAOImpl();
    private PrintWriter out;
    private User loggedInUser = null;

    private final Map<String, RequestProcessor> processors = new HashMap<>();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        activeClients.add(this);
        initProcessors();
    }

    private void initProcessors() {
        processors.put("LOGIN",                  this::handleLogin);
        processors.put("REGISTER",               this::handleRegister);
        processors.put("BID",                    this::handleBid);
        processors.put("GET_AUCTION_DETAIL",     this::handleGetDetail);
        processors.put("GET_AVAILABLE_AUCTIONS", this::handleGetAvailableAuctions);
        processors.put("GET_ALL_AUCTIONS",       this::handleGetAllAuctions);
        processors.put("GET_ALL_USERS",          this::handleGetAllUsers);
        processors.put("GET_ADMIN_STATS", this::handleGetAdminStats);
        processors.put("ADMIN_CANCEL_AUCTION", this::handleAdminCancelAuction);
        processors.put("GET_BALANCE",            this::handleGetBalance);
        processors.put("ADD_ITEM",               this::handleAddItem);
        processors.put("UPDATE_ITEM",            this::handleUpdateItem);
        processors.put("DELETE_ITEM",            this::handleDeleteItem);
        processors.put("GET_MY_ITEMS",           this::handleGetMyItems);
        processors.put("CREATE_AUCTION",         this::handleCreateAuction);
        processors.put("DELETE_AUCTION",         this::handleDeleteAuction);
        processors.put("SET_AUTO_BID",           this::handleSetAutoBid);
        processors.put("CANCEL_AUTO_BID",        this::handleCancelAutoBid);
        processors.put("DEPOSIT",                this::handleDeposit);
        processors.put("GET_MY_AUCTIONS",        this::handleGetMyAuctions);
        processors.put("GET_BID_HISTORY",        this::handleGetBidHistory);
        processors.put("GET_MY_WON_AUCTIONS",    this::handleGetMyWonAuctions);
    }
    private MessageDTO handleAdminCancelAuction(MessageDTO request) {
        if (loggedInUser == null || !loggedInUser.getRole().equalsIgnoreCase("ADMIN")) {
            return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED", "Không có quyền Admin!");
        }

        try {
            int auctionId = Integer.parseInt(request.getPayload().trim());

            AuctionRoom room = AuctionService.getInstance().getActiveRooms()
                    .stream()
                    .filter(r -> r.getId() == auctionId)
                    .findFirst()
                    .orElse(null);

            if (room == null) {
                return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED", "Không tìm thấy phiên đấu giá!");
            }

            if (room.getStatus() == AuctionStatus.PAID) {
                return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED",
                        "Phiên đã thanh toán, không thể hủy!");
            }

            room.setStatus(AuctionStatus.CANCELED);
            auctionDAO.update(room);
            AuctionService.getInstance().reloadFromDatabase();

            broadcast(gson.toJson(new MessageDTO("AUCTION_CANCELED", String.valueOf(auctionId))));

            return new MessageDTO("ADMIN_CANCEL_AUCTION_SUCCESS",
                    "Đã hủy phiên đấu giá #" + auctionId);
        } catch (Exception e) {
            return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED",
                    "Lỗi hủy phiên: " + e.getMessage());
        }
    }
    private MessageDTO handleGetAdminStats(MessageDTO request) {
        if (loggedInUser == null || !loggedInUser.getRole().equalsIgnoreCase("ADMIN")) {
            return new MessageDTO("ERROR", "Không có quyền truy cập!");
        }

        try {
            int totalUsers = userDAO.findAll().size();
            int totalItems = itemDAO.findAll().size();

            BigDecimal revenue = AuctionService.getInstance().getActiveRooms()
                    .stream()
                    .filter(r -> r.getStatus() == AuctionStatus.PAID)
                    .map(r -> r.getCurrentPrice() != null ? r.getCurrentPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("totalItems", totalItems);
            stats.put("revenue", revenue.longValue());

            return new MessageDTO("ADMIN_STATS", gson.toJson(stats));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy thống kê admin: " + e.getMessage());
        }
    }

    // ===================== BALANCE =====================
    private MessageDTO handleGetMyAuctions(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            List<Map<String, Object>> result = AuctionService.getInstance().getActiveRooms()
                    .stream()
                    .filter(r -> r.getSellerID() == loggedInUser.getUserId())
                    .map(r -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("auctionId",    r.getId());
                        m.put("itemId",       r.getItem() != null ? r.getItem().getItemId() : 0);
                        m.put("itemName",     r.getItem() != null ? r.getItem().getName() : "");
                        m.put("currentPrice", r.getCurrentPrice() != null
                                ? r.getCurrentPrice().doubleValue() : 0);
                        m.put("status",       r.getStatus().name());
                        return m;
                    }).collect(Collectors.toList());
            return new MessageDTO("MY_AUCTIONS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleGetBalance(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            User freshUser = userDAO.findByUsername(loggedInUser.getUsername());
            if (freshUser != null) {
                this.loggedInUser = freshUser;
                return new MessageDTO("BALANCE_DATA", freshUser.getAccountBalance().toPlainString());
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy số dư: " + e.getMessage());
        }
        return new MessageDTO("BALANCE_DATA", loggedInUser.getAccountBalance().toPlainString());
    }

    // ===================== DEPOSIT =====================

    private MessageDTO handleDeposit(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            double amount = Double.parseDouble(request.getPayload().trim());
            if (amount <= 0) return new MessageDTO("DEPOSIT_FAILED", "Số tiền không hợp lệ!");

            BigDecimal depositAmount = BigDecimal.valueOf(amount);
            loggedInUser.updateBalance(depositAmount);
            userDAO.update(loggedInUser);

            System.out.println(">>> [Nạp tiền] " + loggedInUser.getUsername() + " nạp " + amount);
            return new MessageDTO("DEPOSIT_SUCCESS", loggedInUser.getAccountBalance().toPlainString());
        } catch (Exception e) {
            return new MessageDTO("DEPOSIT_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    // ===================== ITEMS =====================

    // ===================== ROLE GUARDS =====================
    /** Trả về null nếu user là Seller, ngược lại trả về MessageDTO lỗi. */
    private MessageDTO requireSeller() {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        if (!(loggedInUser instanceof Seller)) {
            return new MessageDTO("ERROR", "Chỉ Seller mới được thực hiện hành động này!");
        }
        return null;
    }

    private MessageDTO requireBidder() {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        if (!(loggedInUser instanceof Bidder)) {
            return new MessageDTO("ERROR", "Chỉ Bidder mới được thực hiện hành động này!");
        }
        return null;
    }

    private MessageDTO handleAddItem(MessageDTO request) {
        MessageDTO err = requireSeller();
        if (err != null) return err;

        try {
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);

            String name        = data.get("name") != null ? data.get("name").toString() : "";
            String description = data.get("description") != null ? data.get("description").toString() : "";
            BigDecimal price   = new BigDecimal(data.get("startingPrice").toString());
            String category    = data.get("category") != null ? data.get("category").toString() : "ART";

            Item newItem = ItemFactory.createItem(category, 0, name, price, description);
            itemDAO.insertWithSellerId(newItem, loggedInUser.getUserId());

            System.out.println(">>> [ADD_ITEM] " + loggedInUser.getUsername() + " thêm: " + name);
            return new MessageDTO("ADD_ITEM_SUCCESS", "Thêm sản phẩm thành công!");
        } catch (Exception e) {
            System.err.println(">>> [ADD_ITEM] Lỗi: " + e.getMessage());
            return new MessageDTO("ADD_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleUpdateItem(MessageDTO request) {
        MessageDTO err = requireSeller();
        if (err != null) return err;
        try {
            // Client gửi gson.toJson(item map) — parse JSON thay vì split(":")
            // để tránh vỡ khi name/description chứa ký tự ':'
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);
            int itemId          = (int) Double.parseDouble(data.get("itemId").toString());
            String name         = data.get("name") != null ? data.get("name").toString() : "";
            String description  = data.get("description") != null ? data.get("description").toString() : "";
            String category     = data.get("category") != null ? data.get("category").toString() : "ART";
            BigDecimal price    = new BigDecimal(data.get("startingPrice").toString());

            Item item = ItemFactory.createItem(category, itemId, name, price, description);
            item.setSeller((Seller) loggedInUser);
            itemDAO.update(item);
            return new MessageDTO("UPDATE_ITEM_SUCCESS", "Cập nhật thành công!");
        } catch (Exception e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleDeleteItem(MessageDTO request) {
        MessageDTO err = requireSeller();
        if (err != null) return err;
        try {
            int itemId = (int) Double.parseDouble(request.getPayload().trim());
            itemDAO.delete(itemId);
            return new MessageDTO("DELETE_ITEM_SUCCESS", "Xóa sản phẩm thành công!");
        } catch (Exception e) {
            return new MessageDTO("DELETE_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleGetMyItems(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            List<Item> items = itemDAO.findBySellerId(loggedInUser.getUserId());
            List<Map<String, Object>> result = items.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemId",        i.getItemId());
                m.put("name",          i.getName());
                m.put("description",   i.getDescription());
                m.put("category",      i.getCategoryInfo());
                m.put("startingPrice", i.getStartingPrice());
                return m;
            }).collect(Collectors.toList());
            return new MessageDTO("MY_ITEMS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi: " + e.getMessage());
        }
    }

    // ===================== AUCTIONS =====================

    private MessageDTO handleCreateAuction(MessageDTO request) {
        MessageDTO err = requireSeller();
        if (err != null) return err;
        try {
            String[] data = request.getPayload().split(":");
            int itemId = (int) Double.parseDouble(data[0]);
            String startTime = data[1] + ":" + data[2];
            int durationMinutes = Integer.parseInt(data[3]);

            Item item = itemDAO.findById(itemId);
            if (item == null) return new MessageDTO("ERROR", "Không tìm thấy sản phẩm!");

            // Đảm bảo seller chỉ tạo auction cho item của chính mình
            if (item.getSeller() != null
                    && item.getSeller().getUserId() != loggedInUser.getUserId()) {
                return new MessageDTO("ERROR",
                        "Bạn không có quyền tạo phiên đấu giá cho sản phẩm này!");
            }

            LocalDateTime startDateTime = LocalDateTime.parse(startTime,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            LocalDateTime endTime = startDateTime.plusMinutes(durationMinutes);

            AuctionRoom room = new AuctionRoom(0, loggedInUser.getUserId(), item, startDateTime, endTime);
            room.setStatus(AuctionStatus.OPEN);
            room.setCurrentPrice(item.getStartingPrice());
            auctionDAO.insert(room);

            AuctionService.getInstance().reloadFromDatabase();

            return new MessageDTO("CREATE_AUCTION_SUCCESS", "Tạo phòng đấu giá thành công!");
        } catch (Exception e) {
            System.err.println("CREATE_AUCTION lỗi: " + e.getMessage());
            return new MessageDTO("CREATE_AUCTION_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleDeleteAuction(MessageDTO request) {
        MessageDTO err = requireSeller();
        if (err != null) return err;
        try {
            int auctionId = Integer.parseInt(request.getPayload().trim());
            auctionDAO.delete(auctionId);
            return new MessageDTO("DELETE_AUCTION_SUCCESS", "Xóa phòng đấu giá thành công!");
        } catch (Exception e) {
            return new MessageDTO("DELETE_AUCTION_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    // ===================== AUTO BIDS =====================

    private MessageDTO handleSetAutoBid(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        if (!(loggedInUser instanceof Bidder)) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Chỉ Bidder mới được đặt auto-bid!");
        }
        try {
            String[] data = request.getPayload().split(":");
            int auctionId  = Integer.parseInt(data[0]);
            BigDecimal max = new BigDecimal(data[1]);
            BigDecimal step= new BigDecimal(data[2]);

            AuctionRoom room = new AuctionRoom();
            room.setId(auctionId);

            AutoBidConfig config = new AutoBidConfig();
            config.setAuctionId(room);
            config.setBidder((Bidder) loggedInUser);
            config.setMaxBid(max);
            config.setIncrement(step);

            autoBidDAO.insert(config);

            // Kích hoạt ngay vòng đấu giá tự động — không phải đợi người khác bid
            AuctionService.getInstance().triggerAutoBidsForRoom(auctionId, (Bidder) loggedInUser);

            return new MessageDTO("SET_AUTO_BID_SUCCESS", "Đặt auto bid thành công!");
        } catch (Exception e) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleCancelAutoBid(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            int auctionId = Integer.parseInt(request.getPayload().trim());
            autoBidDAO.deleteByAuctionId(auctionId);
            return new MessageDTO("CANCEL_AUTO_BID_SUCCESS", "Hủy auto bid thành công!");
        } catch (Exception e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    // ===================== CÁC HÀM CŨ =====================

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                MessageDTO request = gson.fromJson(inputLine, MessageDTO.class);
                RequestProcessor processor = processors.getOrDefault(request.getAction(),
                        (req) -> new MessageDTO("ERROR", "Hành động không hợp lệ"));
                MessageDTO response = processor.process(request);
                if (response != null) out.println(gson.toJson(response));
            }
        } catch (IOException e) {
            System.err.println(">>> Kết nối với Client bị ngắt.");
        } finally {
            cleanup();
        }
    }

    private MessageDTO handleLogin(MessageDTO request) {
        try {
            String[] credentials = request.getPayload().split(":");
            if (credentials.length < 3) return new MessageDTO("LOGIN_FAILED", "Thông tin không đủ");
            String role     = credentials[0];
            String username = credentials[1];
            String password = credentials[2];
            User user = userDAO.findByUsername(username);
            if (user != null
                    && PasswordUtil.verify(password, user.getPasswordHash())
                    && user.getRole().equalsIgnoreCase(role)) {
                this.loggedInUser = user;
                return new MessageDTO("LOGIN_SUCCESS", gson.toJson(user));
            }
        } catch (Exception e) {
            return new MessageDTO("LOGIN_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
        return new MessageDTO("LOGIN_FAILED", "Sai tài khoản hoặc mật khẩu");
    }

    private MessageDTO handleRegister(MessageDTO request) {
        try {
            // Client gửi: "username:password:role:fullName"
            String[] data = request.getPayload().split(":", 4); // tối đa 4 phần
            if (data.length < 3) {
                return new MessageDTO("REGISTER_FAILED", "Dữ liệu đăng ký không đủ!");
            }
            String username = data[0].trim();
            String password = data[1];
            String role     = data[2].trim();
            // fullName (data[3]) hiện chưa lưu vào DB — để dành mở rộng sau

            // Validate cơ bản
            server.utils.Validation.validateUsername(username);
            server.utils.Validation.validatePassword(password);

            User newUser = UserFactory.createUser(role, 0, username);
            newUser.setPasswordHash(PasswordUtil.hash(password));
            userDAO.insert(newUser);
            return new MessageDTO("REGISTER_SUCCESS", "Đăng ký thành công!");
        } catch (server.exceptions.DuplicateDataException e) {
            return new MessageDTO("REGISTER_FAILED", "Tên đăng nhập đã tồn tại!");
        } catch (IllegalArgumentException e) {
            return new MessageDTO("REGISTER_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("REGISTER_FAILED", "Lỗi đăng ký: " + e.getMessage());
        }
    }

    private MessageDTO handleGetDetail(MessageDTO request) {
        try {
            long roomId = Long.parseLong(request.getPayload().trim());
            AuctionRoom room = AuctionService.getInstance().getActiveRooms()
                    .stream().filter(r -> r.getId() == roomId).findFirst().orElse(null);
            if (room == null) return new MessageDTO("ERROR", "Không tìm thấy phòng: " + roomId);
            long secondsLeft = Math.max(0,
                    Duration.between(LocalDateTime.now(), room.getEndTime()).getSeconds());
            String price = room.getCurrentPrice() != null
                    ? room.getCurrentPrice().toPlainString()
                    : room.getItem().getStartingPrice().toPlainString();
            return new MessageDTO("AUCTION_DETAIL_DATA",
                    price + ":" + secondsLeft + ":" + room.getStatus().name());
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy chi tiết: " + e.getMessage());
        }
    }

    private MessageDTO handleGetAvailableAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = AuctionService.getInstance().getActiveRooms()
                    .stream()
                    .filter(r -> r.getStatus() == AuctionStatus.OPEN || r.getStatus() == AuctionStatus.RUNNING)
                    .map(this::roomToMap)
                    .collect(Collectors.toList());
            return new MessageDTO("AUCTION_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách: " + e.getMessage());
        }
    }

    private MessageDTO handleGetAllAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = AuctionService.getInstance().getActiveRooms()
                    .stream().map(this::roomToMap).collect(Collectors.toList());
            return new MessageDTO("AUCTION_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách: " + e.getMessage());
        }
    }

    private MessageDTO handleGetAllUsers(MessageDTO request) {
        if (loggedInUser == null || !loggedInUser.getRole().equalsIgnoreCase("ADMIN"))
            return new MessageDTO("ERROR", "Không có quyền truy cập!");
        try {
            List<User> users = userDAO.findAll();
            List<Map<String, Object>> result = users.stream().map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",       u.getUserId());
                m.put("username", u.getUsername());
                m.put("role",     u.getRole());
                m.put("status",   "ACTIVE");
                return m;
            }).collect(Collectors.toList());
            return new MessageDTO("USER_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách user: " + e.getMessage());
        }
    }

    private Map<String, Object> roomToMap(AuctionRoom room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           room.getId());
        m.put("itemName",     room.getItem() != null ? room.getItem().getName() : "N/A");
        m.put("currentPrice", room.getCurrentPrice() != null
                ? room.getCurrentPrice().doubleValue()
                : (room.getItem() != null ? room.getItem().getStartingPrice().doubleValue() : 0));
        m.put("currentWinner", room.getCurrentWinner() != null
                ? room.getCurrentWinner().getUsername() : "Chưa có");
        m.put("status",       room.getStatus().name());
        return m;
    }

    // ===================== LỊCH SỬ ĐẤU GIÁ =====================

    /**
     * Trả lịch sử bid của 1 phòng từ DB — không bị mất khi client thoát ra.
     * Payload: roomId
     * Response: BID_HISTORY — JSON array [{username, amount, time}, ...]
     */
    private MessageDTO handleGetBidHistory(MessageDTO request) {
        try {
            int roomId = Integer.parseInt(request.getPayload().trim());
            List<BidMessage> bids = bidMessageDAO.getBidHistoryByAuctionRoomId(roomId);

            List<Map<String, Object>> result = bids.stream().map(b -> {
                Map<String, Object> m = new LinkedHashMap<>();
                // Lấy username từ DB theo bidderId
                String username = "Người dùng #" + b.getBidderId();
                try {
                    User u = userDAO.findById(b.getBidderId());
                    if (u != null) username = u.getUsername();
                } catch (Exception ignored) {}
                m.put("username", username);
                m.put("amount",   b.getBidAmount().doubleValue());
                m.put("time",     b.getTimestamp() != null
                        ? b.getTimestamp().toString() : "");
                return m;
            }).collect(Collectors.toList());

            return new MessageDTO("BID_HISTORY", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy lịch sử: " + e.getMessage());
        }
    }

    /**
     * Trả danh sách phiên đấu giá Bidder đã THẮNG (status PAID hoặc FINISHED + winner = mình).
     * Payload: "" (rỗng)
     * Response: WON_AUCTIONS — JSON array [{auctionId, itemName, finalPrice, endTime, status}, ...]
     */
    private MessageDTO handleGetMyWonAuctions(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            List<Map<String, Object>> result = AuctionService.getInstance().getActiveRooms()
                    .stream()
                    .filter(r -> (r.getStatus() == AuctionStatus.PAID
                            || r.getStatus() == AuctionStatus.FINISHED)
                            && r.getCurrentWinner() != null
                            && r.getCurrentWinner().getUserId() == loggedInUser.getUserId())
                    .map(r -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("auctionId",  r.getId());
                        m.put("itemName",   r.getItem() != null ? r.getItem().getName() : "N/A");
                        m.put("finalPrice", r.getCurrentPrice() != null
                                ? r.getCurrentPrice().doubleValue() : 0);
                        m.put("endTime",    r.getEndTime() != null
                                ? r.getEndTime().toString() : "");
                        m.put("status",     r.getStatus().name());
                        return m;
                    }).collect(Collectors.toList());

            return new MessageDTO("WON_AUCTIONS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy kho vật phẩm: " + e.getMessage());
        }
    }

    private MessageDTO handleBid(MessageDTO request) {
        if (this.loggedInUser == null) return new MessageDTO("BID_FAILED", "Vui lòng đăng nhập");
        try {
            String[] data  = request.getPayload().split(":");
            String roomId  = data[0];
            String userBid = data[1];
            String amount  = data[2];
            if (!(this.loggedInUser instanceof Bidder))
                return new MessageDTO("BID_FAILED", "Chỉ Bidder mới được đặt giá!");
            BigDecimal bidAmount = new BigDecimal(amount);
            if (this.loggedInUser.getAccountBalance().compareTo(bidAmount) < 0)
                return new MessageDTO("BID_FAILED", "Số dư ví không đủ! Bạn đang có: "
                        + this.loggedInUser.getAccountBalance().toPlainString() + "đ.");
            String result = AuctionService.getInstance().handleBidRequest(
                    Long.parseLong(roomId), (Bidder) this.loggedInUser, Double.parseDouble(amount));
            if ("SUCCESS".equals(result)) {
                broadcast(gson.toJson(new MessageDTO("UPDATE_PRICE",
                        roomId + ":" + amount + ":" + userBid)));
                return new MessageDTO("BID_SUCCESS", "Đặt giá thành công");
            }
            return new MessageDTO("BID_FAILED", result);
        } catch (Exception e) {
            return new MessageDTO("BID_FAILED", "Lỗi xử lý đặt giá: " + e.getMessage());
        }
    }

    public static void broadcast(String json) {
        ClientHandler.activeClients.forEach(c -> { if (c.out != null) c.out.println(json); });
    }

    private void cleanup() {
        ClientHandler.activeClients.remove(this);
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    interface RequestProcessor {
        MessageDTO process(MessageDTO request);
    }
}
