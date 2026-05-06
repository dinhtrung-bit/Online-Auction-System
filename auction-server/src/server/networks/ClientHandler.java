package server.networks;

import com.google.gson.Gson;
import server.models.users.Seller;
import server.networks.dto.MessageDTO;
import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.AutoBidDAOimpl;
import server.dao.impl.BidMessageDAOImpl;
import server.dao.impl.ItemDAOimpl;
import server.dao.impl.UserDAOimpl;
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
    private final UserDAO userDAO = new UserDAOimpl();
    private final ItemDAO itemDAO = new ItemDAOimpl();
    private final AuctionRoomDAO auctionDAO = new AuctionRoomDAOImpl();
    private final BidMessageDAO bidMessageDAO = new BidMessageDAOImpl();
    private final AutoBidDAO autoBidDAO = new AutoBidDAOimpl();
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
        processors.put("GET_BALANCE",            this::handleGetBalance);

        // items
        processors.put("ADD_ITEM",               this::handleAddItem);
        processors.put("UPDATE_ITEM",            this::handleUpdateItem);
        processors.put("DELETE_ITEM",            this::handleDeleteItem);
        processors.put("GET_MY_ITEMS",           this::handleGetMyItems);

        // auctions
        processors.put("CREATE_AUCTION",         this::handleCreateAuction);
        processors.put("DELETE_AUCTION",         this::handleDeleteAuction);

        // auto_bids
        processors.put("SET_AUTO_BID",           this::handleSetAutoBid);
        processors.put("CANCEL_AUTO_BID",        this::handleCancelAutoBid);
    }

    // ===================== BALANCE =====================

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

    // ===================== ITEMS =====================

    private MessageDTO handleAddItem(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            System.out.println(">>> SERVER nhận ADD_ITEM, payload: " + request.getPayload());
            System.out.println(">>> loggedInUser: " + loggedInUser.getUsername() + " | role: " + loggedInUser.getRole() + " | id: " + loggedInUser.getUserId());

            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);

            String name      = (String) data.get("name");
            String artist    = data.get("artist") != null ? (String) data.get("artist") : "";
            BigDecimal price = new BigDecimal(data.get("startingPrice").toString());

            System.out.println(">>> Parsed: name=" + name + " | artist=" + artist + " | price=" + price);

            Item newItem = ItemFactory.createItem("ART", 0, name, price, artist);
            System.out.println(">>> Item created: " + (newItem == null ? "NULL!" : newItem.getName()));

            itemDAO.insertWithSellerId(newItem, loggedInUser.getUserId());
            System.out.println(">>> INSERT thành công!");

            return new MessageDTO("ADD_ITEM_SUCCESS", "Thêm sản phẩm thành công!");
        } catch (Exception e) {
            System.err.println(">>> ADD_ITEM lỗi: " + e.getMessage());
            e.printStackTrace();
            return new MessageDTO("ADD_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleUpdateItem(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            String[] data      = request.getPayload().split(":");
            int itemId         = Integer.parseInt(data[0]);
            String name        = data[1];
            String description = data[2];
            String category    = data[3];
            BigDecimal price   = new BigDecimal(data[4]);

            Item item = ItemFactory.createItem(category, itemId, name, price, description);
            item.setSeller((Seller) loggedInUser); // cast sang Seller
            itemDAO.update(item);
            return new MessageDTO("UPDATE_ITEM_SUCCESS", "Cập nhật thành công!");
        } catch (Exception e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleDeleteItem(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            int itemId = Integer.parseInt(request.getPayload().trim());
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
                m.put("itemId",       i.getItemId());
                m.put("name",         i.getName());
                m.put("description",  i.getDescription());
                m.put("category",     i.getCategoryInfo());
                m.put("startingPrice",i.getStartingPrice());
                return m;
            }).collect(Collectors.toList());
            return new MessageDTO("MY_ITEMS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi: " + e.getMessage());
        }
    }

    // ===================== AUCTIONS =====================

    private MessageDTO handleCreateAuction(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            // format: "itemId:startTime:durationMinutes"
            // startTime format: "yyyy-MM-ddTHH:mm" (ISO)
            String[] data = request.getPayload().split(":");
            int itemId = Integer.parseInt(data[0]);
            String startTimeStr = data[1] + ":" + data[2]; // giờ:phút
            int durationMinutes = Integer.parseInt(data[3]);

            Item item = itemDAO.findById(itemId);
            if (item == null) return new MessageDTO("ERROR", "Không tìm thấy sản phẩm!");

            LocalDateTime startTime = LocalDateTime.parse(startTimeStr,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            LocalDateTime endTime = startTime.plusMinutes(durationMinutes);

            AuctionRoom room = new AuctionRoom(0, loggedInUser.getUserId(), item, startTime, endTime);
            room.setStatus(AuctionStatus.OPEN);
            room.setCurrentPrice(item.getStartingPrice());
            auctionDAO.insert(room);

            // Thêm vào RAM của AuctionService để autoUpdateStatuses() theo dõi
            AuctionService.getInstance().getActiveRooms(); // trigger load

            return new MessageDTO("CREATE_AUCTION_SUCCESS", "Tạo phòng đấu giá thành công!");
        } catch (Exception e) {
            return new MessageDTO("CREATE_AUCTION_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleDeleteAuction(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            int auctionId = Integer.parseInt(request.getPayload().trim());
            auctionDAO.delete(auctionId);
            return new MessageDTO("DELETE_AUCTION_SUCCESS", "Xóa phòng đấu giá thành công!");
        } catch (Exception e) {
            return new MessageDTO( "DELETE_AUCTION_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    // ===================== AUTO BIDS =====================

    private MessageDTO handleSetAutoBid(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            // format: "auctionId:maxBid:incrementStep"
            String[] data  = request.getPayload().split(":");
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
            return new MessageDTO("SET_AUTO_BID_SUCCESS", "Đặt auto bid thành công!");
        } catch (Exception e) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO handleCancelAutoBid(MessageDTO request) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            // format: "auctionId"
            int auctionId = Integer.parseInt(request.getPayload().trim());
            autoBidDAO.deleteByAuctionId(auctionId);
            return new MessageDTO("CANCEL_AUTO_BID_SUCCESS", "Hủy auto bid thành công!");
        } catch (Exception e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    // ===================== CÁC HÀM CŨ GIỮ NGUYÊN =====================

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
            String[] data = request.getPayload().split(":");
            User newUser = UserFactory.createUser(data[2], 0, data[0]);
            newUser.setPasswordHash(PasswordUtil.hash(data[1]));
            userDAO.insert(newUser);
            return new MessageDTO("REGISTER_SUCCESS", "Đăng ký thành công!");
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
            return new MessageDTO("AUCTION_DETAIL_DATA", price + ":" + secondsLeft + ":" + room.getStatus().name());
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
                broadcast(gson.toJson(new MessageDTO("UPDATE_PRICE", roomId + ":" + amount + ":" + userBid)));
                return new MessageDTO("BID_SUCCESS", "Đặt giá thành công");
            }
            return new MessageDTO("BID_FAILED", result);
        } catch (Exception e) {
            return new MessageDTO("BID_FAILED", "Lỗi xử lý đặt giá: " + e.getMessage());
        }
    }

    public static void broadcast(String json) {
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

    @FunctionalInterface
    interface RequestProcessor {
        MessageDTO process(MessageDTO request);
    }
}