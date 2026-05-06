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
import server.networks.ClientHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class AuctionService {
    private static AuctionService instance;

    private final AuctionRoomDAO roomDAO = new AuctionRoomDAOImpl();
    private final ItemDAO itemDAO = new ItemDAOimpl();
    private final BidMessageDAO bidDAO = new BidMessageDAOImpl();
    private final UserDAO userDAO = new UserDAOimpl(); // MỚI: Thêm DAO để cập nhật tiền user

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
            System.out.println(">>> [Manager] Đã nạp dữ liệu từ Database vào RAM.");
        } catch (Exception e) {
            System.err.println("Lỗi nạp dữ liệu: " + e.getMessage());
        }
    }

    // MỚI: Cập nhật hàm này để nhận thêm sellerID
    public void createNewAuction(int sellerID, Item item, LocalDateTime endTime) {
        Long roomId = System.currentTimeMillis();
        AuctionRoom newRoom = new AuctionRoom(roomId.intValue(), sellerID, item, LocalDateTime.now(), endTime);
        activeRooms.put(roomId, newRoom);

        CompletableFuture.runAsync(() -> {
            try {
                itemDAO.insert(item);
                System.out.println(">>> [Manager] Đã lưu phiên đấu giá mới vào DB.");
            } catch (Exception e) {
                System.err.println("Lỗi lưu DB: " + e.getMessage());
            }
        });
    }

    public String handleBidRequest(Long roomId, Bidder bidder, double amount) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return "LỖI: Không tìm thấy phòng đấu giá!";

        BigDecimal bidAmount = BigDecimal.valueOf(amount);
        BigDecimal oldPrice;

        synchronized (room) {
            try {
                oldPrice = room.getCurrentPrice();
                room.placeBid(bidder, bidAmount);
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.update(room, oldPrice);

                BidMessage bid = new BidMessage(0, bidder.getUserId(), roomId.intValue(), bidAmount);
                bidDAO.insert(bid);

                System.out.println(">>> [DB Success] Phòng " + roomId + ": " + bidder.getUsername() + " bid " + amount);
            } catch (Exception e) {
                System.err.println(">>> [DB Error] Lỗi lưu lịch sử đặt giá: " + e.getMessage());
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
                    System.out.println(">>> [Hệ thống] Room " + room.getId() + " START.");

                    // THÊM: Broadcast cho tất cả client biết room vừa bắt đầu
                    ClientHandler.broadcast(new com.google.gson.Gson().toJson(
                            new server.networks.dto.MessageDTO("AUCTION_STARTED", String.valueOf(room.getId()))
                    ));
                }
                else if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                    room.setStatus(AuctionStatus.FINISHED);
                    processAuctionSettlement(room);
                    updateRoomInDB(room);

                    // THÊM: Broadcast cho tất cả client biết room vừa kết thúc
                    ClientHandler.broadcast(new com.google.gson.Gson().toJson(
                            new server.networks.dto.MessageDTO("AUCTION_FINISHED", String.valueOf(room.getId()))
                    ));
                }
            }
        });
    }

    // MỚI: Xử lý trừ tiền và cộng tiền tự động
    private void processAuctionSettlement(AuctionRoom room) {
        User winner = room.getCurrentWinner();
        BigDecimal finalPrice = room.getCurrentPrice();

        // Không có ai đặt giá
        if (winner == null || finalPrice == null) {
            room.setStatus(AuctionStatus.CANCELED);
            System.out.println(">>> [Hệ thống] Room " + room.getId() + " CANCELED (Không có người mua).");
            return;
        }

        try {
            User seller = userDAO.findById(room.getSellerID());

            // Kiểm tra số dư người thắng
            if (winner.getAccountBalance().compareTo(finalPrice) >= 0) {
                // Trừ tiền người thắng và cộng cho người bán
                winner.updateBalance(finalPrice.negate());
                seller.updateBalance(finalPrice);

                // Lưu lại Database
                userDAO.update(winner);
                userDAO.update(seller);

                room.setStatus(AuctionStatus.PAID);
                System.out.println(">>> [Thanh toán] Room " + room.getId() + " SUCCESS: " + winner.getUsername() + " đã trả " + finalPrice);
            } else {
                room.setStatus(AuctionStatus.CANCELED);
                System.out.println(">>> [Thanh toán] Room " + room.getId() + " FAILED: Người thắng không đủ số dư.");
            }
        } catch (Exception e) {
            room.setStatus(AuctionStatus.CANCELED);
            System.err.println(">>> [Lỗi] Kết toán thất bại: " + e.getMessage());
        }
    }

    private void updateRoomInDB(AuctionRoom room) {
        BigDecimal currentPrice = room.getCurrentPrice();
        CompletableFuture.runAsync(() -> {
            try {
                roomDAO.update(room, currentPrice);
            } catch (Exception e) {
                System.err.println(">>> [Lỗi DB] Update status thất bại: " + e.getMessage());
            }
        });
    }

    public List<AuctionRoom> getActiveRooms() {
        return new ArrayList<>(activeRooms.values());
    }
}