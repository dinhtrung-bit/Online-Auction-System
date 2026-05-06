package server.services;

import server.dao.impl.AuctionRoomDAOImpl;
import server.dao.impl.ItemDAOimpl;
import server.dao.impl.BidMessageDAOImpl;
import server.dao.impl.UserDAOimpl;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.BidMessage;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;

public class AuctionService {
    private static AuctionService instance;
    private final Gson gson = new Gson();

    // [FIX 4] Khai báo Logger để tracking lỗi rõ ràng hơn
    private static final Logger logger = Logger.getLogger(AuctionService.class.getName());

    private final AuctionRoomDAO roomDAO = new AuctionRoomDAOImpl();
    private final ItemDAO itemDAO = new ItemDAOimpl();
    private final BidMessageDAO bidDAO = new BidMessageDAOImpl();
    private final UserDAO userDAO = new UserDAOimpl();

    private ConcurrentHashMap<Long, AuctionRoom> activeRooms;

    private AuctionService() {
        activeRooms = new ConcurrentHashMap<>();
        loadRoomsFromDatabase();
    }

    public static synchronized AuctionService getInstance() {
        if (instance == null) {
            instance = new AuctionService();
        }
        return instance;
    }

    private void loadRoomsFromDatabase() {
        try {
            List<AuctionRoom> rooms = roomDAO.findAll();
            rooms.forEach(r -> activeRooms.put((long)r.getId(), r));
            logger.info(">>> [Manager] Đã nạp dữ liệu từ Database vào RAM.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Lỗi nạp dữ liệu: " + e.getMessage(), e);
        }
    }

    public void createNewAuction(int sellerID, Item item, LocalDateTime endTime) {
        Long roomId = System.currentTimeMillis();
        AuctionRoom newRoom = new AuctionRoom(roomId.intValue(), sellerID, item, LocalDateTime.now(), endTime);
        activeRooms.put(roomId, newRoom);

        CompletableFuture.runAsync(() -> {
            try {
                itemDAO.insert(item);
                logger.info(">>> [Manager] Đã lưu phiên đấu giá mới vào DB.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Lỗi lưu DB: " + e.getMessage(), e);
            }
        });
    }

    // [FIX 1] Tham số bidAmount là BigDecimal
    public String handleBidRequest(Long roomId, Bidder bidder, BigDecimal bidAmount) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return "LỖI: Không tìm thấy phòng đấu giá!";

        BigDecimal oldPrice = room.getCurrentPrice();

        // [FIX 3] Đã loại bỏ synchronized (room) ở đây vì hàm room.placeBid() đã được khóa ở tầng Entity
        try {
            room.placeBid(bidder, bidAmount);
        } catch (Exception e) {
            return e.getMessage();
        }

        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.update(room, oldPrice);

                BidMessage bid = new BidMessage(0, bidder.getUserId(), roomId.intValue(), bidAmount);
                bidDAO.insert(bid);

                logger.info(">>> [DB Success] Phòng " + roomId + ": " + bidder.getUsername() + " bid " + bidAmount);
            } catch (Exception e) {
                // [FIX 4] Sử dụng Logger bắt trọn stack trace
                logger.log(Level.SEVERE, ">>> [DB Error] Lỗi lưu lịch sử đặt giá: " + e.getMessage(), e);
            }
        });

        return "SUCCESS";
    }

    public void autoUpdateStatuses() {
        LocalDateTime now = LocalDateTime.now();

        activeRooms.values().forEach(room -> {
            if (room.getStatus() != AuctionStatus.FINISHED && room.getStatus() != AuctionStatus.PAID && room.getStatus() != AuctionStatus.CANCELED) {
                if (room.getStatus() == AuctionStatus.OPEN && !now.isBefore(room.getStarttime())) {
                    room.setStatus(AuctionStatus.RUNNING);
                    updateRoomInDB(room);
                    logger.info(">>> [Hệ thống] Room " + room.getId() + " START.");
                }
                else if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                    room.setStatus(AuctionStatus.FINISHED);
                    // Kết toán tiền tự động
                    processAuctionSettlement(room);
                    updateRoomInDB(room);
                    // Thông báo cho tất cả Client
                    server.networks.ClientHandler.broadcast(
                            gson.toJson(new server.networks.dto.MessageDTO(
                                    "AUCTION_FINISHED", String.valueOf(room.getId())))
                    );
                    logger.info(">>> [Broadcast] Phòng " + room.getId() + " FINISHED → đã thông báo tất cả Client.");
                }
            }
        });
    }

    private void processAuctionSettlement(AuctionRoom room) {
        User winner = room.getCurrentWinner();
        BigDecimal finalPrice = room.getCurrentPrice();

        // Không có ai đặt giá
        if (winner == null || finalPrice == null) {
            room.setStatus(AuctionStatus.CANCELED);
            logger.info(">>> [Hệ thống] Room " + room.getId() + " CANCELED (Không có người mua).");
            return;
        }

        try {
            User seller = userDAO.findById(room.getSellerID());

            // Kiểm tra số dư người thắng
            if (winner.getAccountBalance().compareTo(finalPrice) >= 0) {

                // [FIX 2] Sử dụng hàm transferMoney chạy trên 1 Transaction duy nhất trong CSDL
                boolean transactionSuccess = userDAO.transferMoney(winner.getUserId(), seller.getUserId(), finalPrice);

                if (transactionSuccess) {
                    // Chỉ cập nhật dữ liệu trên RAM khi Database đã xác nhận Transaction an toàn
                    winner.updateBalance(finalPrice.negate());
                    seller.updateBalance(finalPrice);

                    room.setStatus(AuctionStatus.PAID);
                    logger.info(">>> [Thanh toán] Room " + room.getId() + " SUCCESS: " + winner.getUsername() + " đã trả " + finalPrice);
                } else {
                    room.setStatus(AuctionStatus.CANCELED);
                    logger.warning(">>> [Thanh toán] Room " + room.getId() + " FAILED: Lỗi giao dịch hệ thống.");
                }

            } else {
                room.setStatus(AuctionStatus.CANCELED);
                logger.warning(">>> [Thanh toán] Room " + room.getId() + " FAILED: Người thắng không đủ số dư.");
            }
        } catch (Exception e) {
            room.setStatus(AuctionStatus.CANCELED);
            logger.log(Level.SEVERE, ">>> [Lỗi] Kết toán thất bại: " + e.getMessage(), e);
        }
    }

    private void updateRoomInDB(AuctionRoom room) {
        BigDecimal currentPrice = room.getCurrentPrice();
        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.update(room, currentPrice);
            } catch (Exception e) {
                logger.log(Level.SEVERE, ">>> [Lỗi DB] Update status thất bại: " + e.getMessage(), e);
            }
        });
    }

    public List<AuctionRoom> getActiveRooms() {
        return new ArrayList<>(activeRooms.values());
    }
}