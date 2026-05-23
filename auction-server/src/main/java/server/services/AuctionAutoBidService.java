package server.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.AutoBidDAO;
import server.dao.interfaces.BidMessageDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AutoBidConfig;
import server.models.auction.BidRecord;
import server.models.users.Bidder;
import server.models.users.User;

/**
 * Chỉ phụ trách auto bid.
 * AUTO_BID_EXCEEDED chỉ gửi tới đúng user có AutoBid vượt giới hạn,
 * không broadcast tới tất cả người trong phòng.
 */
public class AuctionAutoBidService {

    private static final int AUTO_BID_MAX_DEPTH = 20;
    /** Độ trễ cố định (ms) giữa mỗi lượt auto-bid để tránh bid liên tiếp quá nhanh */
    private static final long AUTO_BID_DELAY_MS = 2000;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "autobid-scheduler");
                t.setDaemon(true);
                return t;
            });

    private final ConcurrentHashMap<Long, AuctionRoom> activeRooms;
    private final AuctionRoomDAO roomDAO;
    private final BidMessageDAO bidDAO;
    private final UserDAO userDAO;
    private final AutoBidDAO autoBidDAO;
    private final AuctionNotificationService notificationService;

    public AuctionAutoBidService(
            ConcurrentHashMap<Long, AuctionRoom> activeRooms,
            AuctionRoomDAO roomDAO,
            BidMessageDAO bidDAO,
            UserDAO userDAO,
            AutoBidDAO autoBidDAO,
            AuctionNotificationService notificationService) {
        this.activeRooms = activeRooms;
        this.roomDAO = roomDAO;
        this.bidDAO = bidDAO;
        this.userDAO = userDAO;
        this.autoBidDAO = autoBidDAO;
        this.notificationService = notificationService;
    }

    public void registerAutoBid(int auctionId, Bidder bidder, BigDecimal maxBid, BigDecimal step)
            throws Exception {
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder không hợp lệ.");
        }
        if (maxBid == null || maxBid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá tối đa phải lớn hơn 0.");
        }
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bước nhảy phải lớn hơn 0.");
        }

        AuctionRoom room = activeRooms.get((long) auctionId);
        if (room == null) {
            throw new IllegalArgumentException("Không tìm thấy phiên đấu giá.");
        }
        if (room.getSellerID() == bidder.getUserId()) {
            throw new IllegalArgumentException("Seller không được bật auto bid cho sản phẩm của mình.");
        }

        AutoBidConfig config = new AutoBidConfig(auctionId, bidder, maxBid, step);
        autoBidDAO.insert(config);
        triggerAutoBidsForRoom(auctionId, bidder);
    }

    public void cancelAutoBid(int auctionId, int bidderId) throws Exception {
        autoBidDAO.deleteByAuctionIdAndBidderId(auctionId, bidderId);
    }

    public void triggerAutoBidsForRoom(long roomId, Bidder trigger) {
        AuctionRoom room = activeRooms.get(roomId);
        if (room == null) return;
        // Lên lịch xử lý auto-bid sau 2 giây để tránh bid liên tiếp quá nhanh
        scheduler.schedule(
                () -> processAutoBids(room, trigger, 0),
                AUTO_BID_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public void processAutoBids(AuctionRoom room, Bidder lastBidder, int depth) {
        if (depth > AUTO_BID_MAX_DEPTH) return;

        try {
            List<AutoBidConfig> autoBids = autoBidDAO.getAutoBidsByAuctionId(room.getId());
            if (autoBids == null || autoBids.isEmpty()) return;

            for (AutoBidConfig config : autoBids) {
                if (!processSingleAutoBid(room, lastBidder, config, depth)) continue;
                return;
            }
        } catch (Exception e) {
            System.err.println(">>> [AutoBid Error] " + e.getMessage());
        }
    }

    private boolean processSingleAutoBid(
            AuctionRoom room, Bidder lastBidder, AutoBidConfig config, int depth) throws Exception {
        Bidder autoBidder = config.getBidder();
        if (autoBidder == null) {
            return false;
        }
        if (lastBidder != null && autoBidder.getUserId() == lastBidder.getUserId()) {
            return false;
        }
        if (room.getCurrentWinner() != null
                && room.getCurrentWinner().getUserId() == autoBidder.getUserId()) {
            return false;
        }
        if (room.getSellerID() == autoBidder.getUserId()) {
            return false;
        }

        BigDecimal increment = config.getIncrement() != null ? config.getIncrement() : BigDecimal.ONE;
        BigDecimal nextBid = room.getCurrentPrice().add(increment);

        if (nextBid.compareTo(config.getMaxBid()) > 0) {
            // Xoa config khoi DB de khong bi kich hoat lai o cac lan bid tiep theo
            try {
                autoBidDAO.deleteByAuctionIdAndBidderId(room.getId(), autoBidder.getUserId());
            } catch (Exception ex) {
                System.err.println(">>> [AutoBid] Khong xoa duoc config sau EXCEEDED: " + ex.getMessage());
            }
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("auctionId", room.getId());
            payload.put("userId", autoBidder.getUserId());
            payload.put("username", autoBidder.getUsername());
            payload.put("message", "AutoBid đã đạt giới hạn tối đa và tự dừng.");

            notificationService.sendToUserInRoom(
                    room.getId(),
                    autoBidder.getUserId(),
                    "AUTO_BID_EXCEEDED",
                    new com.google.gson.Gson().toJson(payload)
            );
            return false;
        }

        User fullUser = userDAO.findById(autoBidder.getUserId());
        if (!(fullUser instanceof Bidder fullBidder)) {
            return false;
        }
        if (fullBidder.getAccountBalance().compareTo(nextBid) < 0) {
            return false;
        }

        BigDecimal oldPrice = room.getCurrentPrice();
        room.placeBid(fullBidder, nextBid);
        roomDAO.updateWithOptimisticLock(room, oldPrice);
        bidDAO.insert(new BidRecord(room.getId(), fullBidder.getUserId(), nextBid));

        notificationService.broadcastToRoom(room.getId(), "UPDATE_PRICE",
                room.getId() + ":" + nextBid.toPlainString() + ":" + fullBidder.getUsername());

        System.out.println(">>> [AutoBid] " + fullBidder.getUsername()
                + " bid " + nextBid + " vào phòng " + room.getId());

        // Lên lịch xử lý auto-bid tiếp theo sau 2 giây
        final int nextDepth = depth + 1;
        final Bidder bidderRef = fullBidder;
        scheduler.schedule(
                () -> processAutoBids(room, bidderRef, nextDepth),
                AUTO_BID_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }
}