package server.networks.handlers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.Seller;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.AuctionService;
import server.services.ItemService;

/**
 * AuctionRequestHandler — Xử lý toàn bộ request liên quan đến phiên đấu giá.
 *
 * <p>Sau refactor, class này chỉ chứa logic nghiệp vụ thuần túy:
 *
 * <ul>
 *   <li>Parse payload và utility số → {@link PayloadParser}
 *   <li>Chuyển đổi AuctionRoom → Map → {@link AuctionMapper}
 * </ul>
 */
public class AuctionRequestHandler {

    private final AuctionService auctionService;
    private final ItemService itemService;
    private final Gson gson = new Gson();

    public AuctionRequestHandler(AuctionService auctionService, ItemService itemService) {
        this.auctionService = auctionService;
        this.itemService = itemService;
    }

    // ─── Bidder actions ──────────────────────────────────────────────────────

    public MessageDTO handleBid(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Bidder bidder)) {
            return new MessageDTO("BID_FAILED", "Chỉ Bidder mới được đặt giá!");
        }
        try {
            BidPayload bid = parseBidPayload(request);

            if (bid.amount().compareTo(BigDecimal.ZERO) <= 0) {
                return new MessageDTO("BID_FAILED", "Số tiền đặt giá phải lớn hơn 0.");
            }
            if (loggedInUser.getAccountBalance().compareTo(bid.amount()) < 0) {
                return new MessageDTO(
                        "BID_FAILED",
                        "Số dư ví không đủ! Bạn đang có: "
                                + loggedInUser.getAccountBalance().toPlainString() + "đ.");
            }

            String result = auctionService.handleBidRequest(
                    bid.roomId(), bidder, bid.amount().doubleValue());
            if (!"SUCCESS".equals(result)) {
                return new MessageDTO("BID_FAILED", result);
            }

            return new MessageDTO("BID_SUCCESS", "Đặt giá thành công.");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("BID_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("BID_FAILED", "Lỗi xử lý đặt giá: " + e.getMessage());
        }
    }

    // ─── Query: danh sách & chi tiết phòng ───────────────────────────────────

    public MessageDTO handleGetDetail(MessageDTO request) {
        try {
            long roomId = PayloadParser.parseIdPayload(request.getPayload(), "roomId");
            AuctionRoom room = auctionService.findRoomById(roomId);

            if (room == null) {
                return new MessageDTO("ERROR", "Không tìm thấy phòng: " + roomId);
            }
            return new MessageDTO(
                    "AUCTION_DETAIL_DATA", gson.toJson(AuctionMapper.toDetailMap(room)));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy chi tiết: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAvailableAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> r.getStatus() == AuctionStatus.OPEN
                            || r.getStatus() == AuctionStatus.RUNNING)
                    .map(AuctionMapper::toListMap)
                    .collect(Collectors.toList());
            return new MessageDTO("AUCTION_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAllAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .map(AuctionMapper::toListMap)
                    .collect(Collectors.toList());
            return new MessageDTO("AUCTION_LIST", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAuctionsByStatus(MessageDTO request) {
        try {
            String statusStr = request.getPayload() != null
                    ? request.getPayload().trim().toUpperCase()
                    : "";
            AuctionStatus targetStatus = AuctionMapper.parseStatus(statusStr);

            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> targetStatus == null || r.getStatus() == targetStatus)
                    .map(AuctionMapper::toListMap)
                    .collect(Collectors.toList());

            return new MessageDTO("AUCTION_LIST_BY_STATUS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lọc danh sách: " + e.getMessage());
        }
    }

    public MessageDTO handleGetBidHistory(MessageDTO request, User loggedInUser) {
        try {
            int roomId = (int) PayloadParser.parseIdPayload(request.getPayload(), "roomId");
            return new MessageDTO(
                    "BID_HISTORY", gson.toJson(auctionService.getBidHistory(roomId)));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy lịch sử: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyAuctions(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập");
        }
        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> r.getSellerID() == loggedInUser.getUserId())
                    .map(AuctionMapper::toListMap)
                    .collect(Collectors.toList());
            return new MessageDTO("MY_AUCTIONS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyWonAuctions(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập");
        }
        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> (r.getStatus() == AuctionStatus.PAID
                            || r.getStatus() == AuctionStatus.FINISHED)
                            && r.getCurrentWinner() != null
                            && r.getCurrentWinner().getUserId() == loggedInUser.getUserId())
                    .map(AuctionMapper::toWonMap)
                    .collect(Collectors.toList());
            return new MessageDTO("WON_AUCTIONS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy kho vật phẩm: " + e.getMessage());
        }
    }

    // ─── Seller actions ──────────────────────────────────────────────────────

    public MessageDTO handleCreateAuction(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Seller seller)) {
            return new MessageDTO(
                    "CREATE_AUCTION_FAILED", "Chỉ Seller mới được tạo phiên đấu giá!");
        }
        try {
            CreateAuctionPayload p = parseCreateAuctionPayload(request);

            if (p.durationMinutes() <= 0) {
                return new MessageDTO(
                        "CREATE_AUCTION_FAILED", "Thời lượng phiên đấu giá phải lớn hơn 0 phút.");
            }

            Item item = itemService.findById(p.itemId());
            if (item == null) {
                return new MessageDTO("CREATE_AUCTION_FAILED", "Không tìm thấy sản phẩm.");
            }
            if (item.getSeller() == null || item.getSeller().getUserId() != seller.getUserId()) {
                return new MessageDTO("CREATE_AUCTION_FAILED", "Bạn không sở hữu sản phẩm này.");
            }

            LocalDateTime start = LocalDateTime.parse(p.startTime());
            if (start.isBefore(LocalDateTime.now())) {
                return new MessageDTO(
                        "CREATE_AUCTION_FAILED", "Không thể tạo phiên đấu giá trong quá khứ.");
            }

            auctionService.createAuction(
                    seller.getUserId(), item, start, start.plusMinutes(p.durationMinutes()));

            return new MessageDTO("CREATE_AUCTION_SUCCESS", "Tạo phòng đấu giá thành công!");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("CREATE_AUCTION_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("CREATE_AUCTION_FAILED", "Lỗi tạo phiên: " + e.getMessage());
        }
    }

    public MessageDTO handleDeleteAuction(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Seller)) {
            return new MessageDTO(
                    "DELETE_AUCTION_FAILED", "Chỉ Seller mới được hủy phiên đấu giá!");
        }
        try {
            int auctionId = (int) PayloadParser.parseIdPayload(request.getPayload(), "auctionId");
            String result = auctionService.cancelAuctionBySeller(auctionId, loggedInUser.getUserId());

            if ("SUCCESS".equals(result)) {
                broadcastToRoom(auctionId, "AUCTION_CANCELED", String.valueOf(auctionId));
                return new MessageDTO(
                        "DELETE_AUCTION_SUCCESS", "Đã hủy phiên đấu giá #" + auctionId);
            }
            return new MessageDTO("DELETE_AUCTION_FAILED", result);

        } catch (Exception e) {
            return new MessageDTO("DELETE_AUCTION_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    // ─── Admin actions ───────────────────────────────────────────────────────

    public MessageDTO handleAdminCancelAuction(MessageDTO request, User loggedInUser) {
        if (!isAdmin(loggedInUser)) {
            return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED", "Không có quyền Admin!");
        }
        try {
            int auctionId = (int) PayloadParser.parseIdPayload(request.getPayload(), "auctionId");
            String result = auctionService.cancelAuctionByAdmin(auctionId);

            if ("SUCCESS".equals(result)) {
                broadcastToRoom(auctionId, "AUCTION_CANCELED", String.valueOf(auctionId));
                return new MessageDTO(
                        "ADMIN_CANCEL_AUCTION_SUCCESS", "Đã hủy phiên đấu giá #" + auctionId);
            }
            return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED", result);

        } catch (Exception e) {
            return new MessageDTO(
                    "ADMIN_CANCEL_AUCTION_FAILED", "Lỗi hủy phiên: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAdminStats(MessageDTO request, User loggedInUser, int totalUsers) {
        if (!isAdmin(loggedInUser)) {
            return new MessageDTO("ERROR", "Không có quyền truy cập!");
        }
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalUsers",       totalUsers);
            stats.put("totalItems",       itemService.countAll());
            stats.put("paidAuctions",     countByStatus(AuctionStatus.PAID));
            stats.put("canceledAuctions", countByStatus(AuctionStatus.CANCELED));

            return new MessageDTO("ADMIN_STATS", gson.toJson(stats));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy thống kê admin: " + e.getMessage());
        }
    }

    // ─── Parse payload (logic đặc thù của handler này) ───────────────────────

    private BidPayload parseBidPayload(MessageDTO request) {
        String payload = PayloadParser.requirePayload(request);

        if (payload.startsWith("{")) {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);

            long roomId = PayloadParser.hasKey(data, "roomId")
                    ? PayloadParser.getLong(data, "roomId")
                    : PayloadParser.getLong(data, "auctionId");

            BigDecimal amount;
            if (PayloadParser.hasKey(data, "amount")) {
                amount = PayloadParser.getBigDecimal(data, "amount");
            } else if (PayloadParser.hasKey(data, "bidAmount")) {
                amount = PayloadParser.getBigDecimal(data, "bidAmount");
            } else if (PayloadParser.hasKey(data, "price")) {
                amount = PayloadParser.getBigDecimal(data, "price");
            } else {
                throw new IllegalArgumentException("Thiếu số tiền đặt giá.");
            }

            return new BidPayload(roomId, amount);
        }

        // Legacy format: "roomId:amount"
        String[] parts = payload.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Payload BID không hợp lệ: " + payload);
        }
        long roomId = (long) Double.parseDouble(PayloadParser.cleanNumberText(parts[0]));
        BigDecimal amount = new BigDecimal(PayloadParser.cleanNumberText(parts[1]));
        return new BidPayload(roomId, amount);
    }

    private CreateAuctionPayload parseCreateAuctionPayload(MessageDTO request) {
        String payload = PayloadParser.requirePayload(request);

        if (payload.startsWith("{")) {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);

            int itemId          = PayloadParser.getInt(data, "itemId");
            String startTime    = PayloadParser.getString(data, "startTime", "");
            int durationMinutes = PayloadParser.getInt(data, "durationMinutes");

            if (startTime.isBlank()) {
                throw new IllegalArgumentException("Thiếu startTime.");
            }
            return new CreateAuctionPayload(itemId, startTime, durationMinutes);
        }

        // Legacy format: "itemId:startTime:durationMinutes"
        int firstColon = payload.indexOf(":");
        int lastColon  = payload.lastIndexOf(":");
        if (firstColon <= 0 || lastColon <= firstColon) {
            throw new IllegalArgumentException("Payload tạo phiên không hợp lệ.");
        }

        int itemId          = (int) PayloadParser.toLong(payload.substring(0, firstColon));
        String startTime    = payload.substring(firstColon + 1, lastColon).trim();
        int durationMinutes =
                Integer.parseInt(PayloadParser.cleanNumberText(payload.substring(lastColon + 1)));
        return new CreateAuctionPayload(itemId, startTime, durationMinutes);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void broadcast(String action, String payload) {
        auctionService.getBroadcaster().broadcast(gson.toJson(new MessageDTO(action, payload)));
    }

    /**
     * Broadcast action chỉ tới các client đang ở trong phòng có ID = roomId.
     * Dùng cho AUCTION_CANCELED — chỉ người đang trong phòng đó mới cần thông báo.
     */
    private void broadcastToRoom(long roomId, String action, String payload) {
        var broadcaster = auctionService.getBroadcaster();
        if (broadcaster instanceof server.networks.AuctionBroadcastManager mgr) {
            mgr.broadcastToRoom(roomId, gson.toJson(new MessageDTO(action, payload)));
        } else {
            broadcast(action, payload);
        }
    }

    private long countByStatus(AuctionStatus status) {
        return auctionService.getActiveRooms().stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    // ─── Payload DTOs ────────────────────────────────────────────────────────

    private record BidPayload(long roomId, BigDecimal amount) {}

    private record CreateAuctionPayload(int itemId, String startTime, int durationMinutes) {}
}