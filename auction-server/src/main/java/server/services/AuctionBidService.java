package server.services;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.auction.BidRecord;
import server.models.users.Bidder;
import server.models.users.User;

/**
 * Chỉ phụ trách bid thường.
 * Logic validate, cập nhật giá, lưu bid và gọi auto-bid giữ nguyên từ AuctionService cũ.
 */
public class AuctionBidService {

    private final ConcurrentHashMap<Long, AuctionRoom> activeRooms;
    private final AuctionRoomDAO roomDAO;
    private final BidMessageDAO bidDAO;
    private final UserDAO userDAO;
    private final AuctionAutoBidService autoBidService;
    private final AuctionNotificationService notificationService;
    public AuctionBidService(
            ConcurrentHashMap<Long, AuctionRoom> activeRooms,
            AuctionRoomDAO roomDAO,
            BidMessageDAO bidDAO,
            UserDAO userDAO,
            AuctionAutoBidService autoBidService,
            AuctionNotificationService notificationService) {
        this.activeRooms = activeRooms;
        this.roomDAO = roomDAO;
        this.bidDAO = bidDAO;
        this.userDAO = userDAO;
        this.autoBidService = autoBidService;
        this.notificationService = notificationService;
    }

    public String handleBidRequest(Long roomId, Bidder bidder, double amount) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) {
            return "Không tìm thấy phòng đấu giá.";
        }
        if (bidder == null) {
            return "Người đặt giá không hợp lệ.";
        }

        BigDecimal bidAmount = BigDecimal.valueOf(amount);
        if (bidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Giá đặt phải lớn hơn 0.";
        }

        synchronized (room) {
            try {
                if (room.getSellerID() == bidder.getUserId()) {
                    return "Seller không được tự bid sản phẩm của mình.";
                }
                if (room.getStatus() != AuctionStatus.RUNNING) {
                    return "Phiên đấu giá chưa chạy hoặc đã kết thúc.";
                }

                User freshUser = userDAO.findById(bidder.getUserId());
                if (!(freshUser instanceof Bidder freshBidder)) {
                    return "Tài khoản không phải Bidder.";
                }
                if (freshBidder.getAccountBalance().compareTo(bidAmount) < 0) {
                    return "Số dư không đủ.";
                }

                BigDecimal oldPrice = room.getCurrentPrice();
                room.placeBid(freshBidder, bidAmount);
                roomDAO.updateWithOptimisticLock(room, oldPrice);
                bidDAO.insert(new BidRecord(roomId.intValue(), freshBidder.getUserId(), bidAmount));
                notificationService.broadcastToRoom(roomId, "UPDATE_PRICE",
                        roomId + ":" + bidAmount.toPlainString() + ":" + freshBidder.getUsername());

                autoBidService.processAutoBids(room, freshBidder, 0);

                System.out.println(">>> [Bid] Room " + roomId + ": "
                        + freshBidder.getUsername() + " bid " + bidAmount);
                return "SUCCESS";

            } catch (Exception e) {
                return e.getMessage();
            }
        }
    }
}