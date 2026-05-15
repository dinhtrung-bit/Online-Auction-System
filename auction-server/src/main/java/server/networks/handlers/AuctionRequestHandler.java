package server.networks.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.Seller;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.AuctionService;
import server.services.ItemService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuctionRequestHandler {

    private final AuctionService auctionService;
    private final ItemService itemService;
    private final Gson gson = new Gson();

    public AuctionRequestHandler(AuctionService auctionService, ItemService itemService) {
        this.auctionService = auctionService;
        this.itemService = itemService;
    }

    public MessageDTO handleBid(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Bidder bidder)) {
            return new MessageDTO("BID_FAILED", "Chỉ Bidder mới được đặt giá!");
        }

        try {
            Map<String, Object> data = parseJsonPayload(request);

            long roomId = getLong(data, "roomId");
            BigDecimal bidAmount = getBigDecimal(data, "amount");

            if (bidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return new MessageDTO("BID_FAILED", "Số tiền đặt giá phải lớn hơn 0.");
            }

            if (loggedInUser.getAccountBalance().compareTo(bidAmount) < 0) {
                return new MessageDTO(
                        "BID_FAILED",
                        "Số dư ví không đủ! Bạn đang có: "
                                + loggedInUser.getAccountBalance().toPlainString() + "đ."
                );
            }

            String result = auctionService.handleBidRequest(roomId, bidder, bidAmount.doubleValue());

            if (!"SUCCESS".equals(result)) {
                return new MessageDTO("BID_FAILED", result);
            }

            auctionService.getBroadcaster().broadcast(gson.toJson(
                    new MessageDTO(
                            "UPDATE_PRICE",
                            roomId + ":" + bidAmount.toPlainString() + ":" + bidder.getUsername()
                    )
            ));

            return new MessageDTO("BID_SUCCESS", "Đặt giá thành công.");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("BID_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("BID_FAILED", "Lỗi xử lý đặt giá: " + e.getMessage());
        }
    }

    public MessageDTO handleGetDetail(MessageDTO request) {
        try {
            long roomId = Long.parseLong(request.getPayload().trim());
            AuctionRoom room = auctionService.findRoomById(roomId);

            if (room == null) {
                return new MessageDTO("ERROR", "Không tìm thấy phòng: " + roomId);
            }

            long secondsLeft = Math.max(
                    0,
                    Duration.between(LocalDateTime.now(), room.getEndTime()).getSeconds()
            );

            Item item = room.getItem();

            BigDecimal currentPrice = room.getCurrentPrice() != null
                    ? room.getCurrentPrice()
                    : item != null ? item.getStartingPrice() : BigDecimal.ZERO;

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("auctionId", room.getId());
            detail.put("status", room.getStatus() != null ? room.getStatus().name() : "UNKNOWN");
            detail.put("secondsLeft", secondsLeft);
            detail.put("startTime", room.getStarttime() != null ? room.getStarttime().toString() : "");
            detail.put("endTime", room.getEndTime() != null ? room.getEndTime().toString() : "");
            detail.put("sellerID", room.getSellerID());
            detail.put("currentPrice", currentPrice.doubleValue());
            detail.put("currentWinner", room.getCurrentWinner() != null ? room.getCurrentWinner().getUsername() : "");
            detail.put("bidCount", room.getBidHistory() != null ? room.getBidHistory().size() : 0);

            if (item != null) {
                detail.put("itemId", item.getItemId());
                detail.put("itemName", item.getName());
                detail.put("name", item.getName());
                detail.put("description", item.getDescription());
                detail.put("category", item.getCategoryInfo());
                detail.put("categoryInfo", item.getCategoryInfo());
                detail.put("startingPrice", item.getStartingPrice() != null ? item.getStartingPrice().doubleValue() : 0);
                detail.put("imagePath", item.getImagePath());
                detail.put("bidIncrement", item.getBidIncrement() != null ? item.getBidIncrement().doubleValue() : 0);
            }

            return new MessageDTO("AUCTION_DETAIL_DATA", gson.toJson(detail));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy chi tiết: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAvailableAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> r.getStatus() == AuctionStatus.OPEN || r.getStatus() == AuctionStatus.RUNNING)
                    .map(this::roomToMap)
                    .collect(Collectors.toList());

            return new MessageDTO("AUCTION_LIST", gson.toJson(result));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy danh sách: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAllAuctions(MessageDTO request) {
        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .map(this::roomToMap)
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

            AuctionStatus targetStatus = parseAuctionStatus(statusStr);

            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> targetStatus == null || r.getStatus() == targetStatus)
                    .map(this::roomToMap)
                    .collect(Collectors.toList());

            return new MessageDTO("AUCTION_LIST_BY_STATUS", gson.toJson(result));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lọc danh sách: " + e.getMessage());
        }
    }

    public MessageDTO handleCreateAuction(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Seller seller)) {
            return new MessageDTO("CREATE_AUCTION_FAILED", "Chỉ Seller mới được tạo phiên đấu giá!");
        }

        try {
            Map<String, Object> data = parseJsonPayload(request);

            int itemId = getInt(data, "itemId");
            String startTime = getString(data, "startTime", "");
            int durationMinutes = getInt(data, "durationMinutes");

            if (startTime.isBlank()) {
                return new MessageDTO("CREATE_AUCTION_FAILED", "Thiếu startTime.");
            }

            if (durationMinutes <= 0) {
                return new MessageDTO("CREATE_AUCTION_FAILED", "Thời lượng phiên đấu giá phải lớn hơn 0 phút.");
            }

            Item item = itemService.findById(itemId);

            if (item == null) {
                return new MessageDTO("CREATE_AUCTION_FAILED", "Không tìm thấy sản phẩm.");
            }

            if (item.getSeller() == null || item.getSeller().getUserId() != seller.getUserId()) {
                return new MessageDTO("CREATE_AUCTION_FAILED", "Bạn không sở hữu sản phẩm này.");
            }

            LocalDateTime startDateTime = LocalDateTime.parse(startTime);

            if (startDateTime.isBefore(LocalDateTime.now())) {
                return new MessageDTO("CREATE_AUCTION_FAILED", "Không thể tạo phiên đấu giá trong quá khứ.");
            }

            LocalDateTime endTime = startDateTime.plusMinutes(durationMinutes);

            auctionService.createAuction(seller.getUserId(), item, startDateTime, endTime);

            return new MessageDTO("CREATE_AUCTION_SUCCESS", "Tạo phòng đấu giá thành công!");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("CREATE_AUCTION_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("CREATE_AUCTION_FAILED", "Lỗi tạo phiên: " + e.getMessage());
        }
    }

    public MessageDTO handleDeleteAuction(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Seller)) {
            return new MessageDTO("DELETE_AUCTION_FAILED", "Chỉ Seller mới được hủy phiên đấu giá!");
        }

        try {
            int auctionId = Integer.parseInt(request.getPayload().trim());
            String result = auctionService.cancelAuctionBySeller(auctionId, loggedInUser.getUserId());

            if ("SUCCESS".equals(result)) {
                auctionService.getBroadcaster().broadcast(gson.toJson(
                        new MessageDTO("AUCTION_CANCELED", String.valueOf(auctionId))
                ));

                return new MessageDTO("DELETE_AUCTION_SUCCESS", "Đã hủy phiên đấu giá #" + auctionId);
            }

            return new MessageDTO("DELETE_AUCTION_FAILED", result);

        } catch (Exception e) {
            return new MessageDTO("DELETE_AUCTION_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleAdminCancelAuction(MessageDTO request, User loggedInUser) {
        if (!isAdmin(loggedInUser)) {
            return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED", "Không có quyền Admin!");
        }

        try {
            int auctionId = Integer.parseInt(request.getPayload().trim());
            String result = auctionService.cancelAuctionByAdmin(auctionId);

            if ("SUCCESS".equals(result)) {
                auctionService.getBroadcaster().broadcast(gson.toJson(
                        new MessageDTO("AUCTION_CANCELED", String.valueOf(auctionId))
                ));

                return new MessageDTO("ADMIN_CANCEL_AUCTION_SUCCESS", "Đã hủy phiên đấu giá #" + auctionId);
            }

            return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED", result);

        } catch (Exception e) {
            return new MessageDTO("ADMIN_CANCEL_AUCTION_FAILED", "Lỗi hủy phiên: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAdminStats(MessageDTO request, User loggedInUser, int totalUsers, int pendingDeposits) {
        if (!isAdmin(loggedInUser)) {
            return new MessageDTO("ERROR", "Không có quyền truy cập!");
        }

        try {
            BigDecimal grossSales = auctionService.getActiveRooms().stream()
                    .filter(r -> r.getStatus() == AuctionStatus.PAID)
                    .map(r -> r.getCurrentPrice() != null ? r.getCurrentPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal platformRevenue = grossSales.multiply(new BigDecimal("0.05"));

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("totalItems", itemService.countAll());
            stats.put("revenue", platformRevenue.longValue());
            stats.put("grossSales", grossSales.longValue());
            stats.put("pendingDeposits", pendingDeposits);
            stats.put("paidAuctions", auctionService.getActiveRooms().stream().filter(r -> r.getStatus() == AuctionStatus.PAID).count());
            stats.put("canceledAuctions", auctionService.getActiveRooms().stream().filter(r -> r.getStatus() == AuctionStatus.CANCELED).count());

            return new MessageDTO("ADMIN_STATS", gson.toJson(stats));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy thống kê admin: " + e.getMessage());
        }
    }

    public MessageDTO handleGetAdminRevenueReport(MessageDTO request, User loggedInUser) {
        if (!isAdmin(loggedInUser)) {
            return new MessageDTO("ERROR", "Không có quyền truy cập!");
        }

        try {
            BigDecimal grossSales = BigDecimal.ZERO;
            BigDecimal platformFee = BigDecimal.ZERO;

            int paidCount = 0;
            int runningCount = 0;
            int canceledCount = 0;
            int openCount = 0;
            int finishedUnpaidCount = 0;

            List<Map<String, Object>> rows = new java.util.ArrayList<>();

            for (AuctionRoom r : auctionService.getActiveRooms()) {
                BigDecimal price = r.getCurrentPrice() != null ? r.getCurrentPrice() : BigDecimal.ZERO;
                BigDecimal fee = BigDecimal.ZERO;

                if (r.getStatus() == AuctionStatus.PAID) {
                    paidCount++;
                    grossSales = grossSales.add(price);
                    fee = price.multiply(new BigDecimal("0.05"));
                    platformFee = platformFee.add(fee);
                } else if (r.getStatus() == AuctionStatus.RUNNING) {
                    runningCount++;
                } else if (r.getStatus() == AuctionStatus.CANCELED) {
                    canceledCount++;
                } else if (r.getStatus() == AuctionStatus.OPEN) {
                    openCount++;
                } else if (r.getStatus() == AuctionStatus.FINISHED) {
                    finishedUnpaidCount++;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("auctionId", r.getId());
                row.put("itemName", r.getItem() != null ? r.getItem().getName() : "N/A");
                row.put("sellerId", r.getSellerID());
                row.put("winner", r.getCurrentWinner() != null ? r.getCurrentWinner().getUsername() : "");
                row.put("finalPrice", price.doubleValue());
                row.put("platformFee", fee.doubleValue());
                row.put("sellerPayout", price.subtract(fee).doubleValue());
                row.put("status", r.getStatus() != null ? r.getStatus().name() : "UNKNOWN");
                row.put("startTime", r.getStarttime() != null ? r.getStarttime().toString() : "");
                row.put("endTime", r.getEndTime() != null ? r.getEndTime().toString() : "");
                rows.add(row);
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("grossSales", grossSales.doubleValue());
            report.put("platformRevenue", platformFee.doubleValue());
            report.put("sellerPayout", grossSales.subtract(platformFee).doubleValue());
            report.put("paidCount", paidCount);
            report.put("runningCount", runningCount);
            report.put("openCount", openCount);
            report.put("canceledCount", canceledCount);
            report.put("finishedUnpaidCount", finishedUnpaidCount);
            report.put("feePercent", 5);
            report.put("rows", rows);

            return new MessageDTO("ADMIN_REVENUE_REPORT", gson.toJson(report));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy báo cáo doanh thu: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyAuctions(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập");
        }

        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> r.getSellerID() == loggedInUser.getUserId())
                    .map(this::roomToMap)
                    .collect(Collectors.toList());

            return new MessageDTO("MY_AUCTIONS", gson.toJson(result));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleGetBidHistory(MessageDTO request, User loggedInUser) {
        try {
            int roomId = Integer.parseInt(request.getPayload().trim());
            List<Map<String, Object>> result = auctionService.getBidHistory(roomId);

            return new MessageDTO("BID_HISTORY", gson.toJson(result));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy lịch sử: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyWonAuctions(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập");
        }

        try {
            List<Map<String, Object>> result = auctionService.getActiveRooms().stream()
                    .filter(r -> (r.getStatus() == AuctionStatus.PAID || r.getStatus() == AuctionStatus.FINISHED)
                            && r.getCurrentWinner() != null
                            && r.getCurrentWinner().getUserId() == loggedInUser.getUserId())
                    .map(r -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("auctionId", r.getId());
                        m.put("itemName", r.getItem() != null ? r.getItem().getName() : "N/A");
                        m.put("finalPrice", r.getCurrentPrice() != null ? r.getCurrentPrice().doubleValue() : 0);
                        m.put("endTime", r.getEndTime() != null ? r.getEndTime().toString() : "");
                        m.put("status", r.getStatus().name());
                        return m;
                    })
                    .collect(Collectors.toList());

            return new MessageDTO("WON_AUCTIONS", gson.toJson(result));

        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi lấy kho vật phẩm: " + e.getMessage());
        }
    }

    private Map<String, Object> roomToMap(AuctionRoom room) {
        Map<String, Object> m = new LinkedHashMap<>();
        Item item = room.getItem();

        m.put("id", room.getId());
        m.put("itemId", item != null ? item.getItemId() : 0);
        m.put("itemName", item != null ? item.getName() : "N/A");
        m.put("description", item != null ? item.getDescription() : "");
        m.put("category", item != null ? item.getCategoryInfo() : "");
        m.put("startingPrice", item != null && item.getStartingPrice() != null ? item.getStartingPrice().doubleValue() : 0);
        m.put("imagePath", item != null ? item.getImagePath() : "");
        m.put("bidIncrement", item != null && item.getBidIncrement() != null ? item.getBidIncrement().doubleValue() : 0);
        m.put("currentPrice", room.getCurrentPrice() != null
                ? room.getCurrentPrice().doubleValue()
                : item != null && item.getStartingPrice() != null ? item.getStartingPrice().doubleValue() : 0);
        m.put("currentWinner", room.getCurrentWinner() != null ? room.getCurrentWinner().getUsername() : "Chưa có");
        m.put("status", room.getStatus() != null ? room.getStatus().name() : "UNKNOWN");
        m.put("startTime", room.getStarttime() != null ? room.getStarttime().toString() : "");
        m.put("endTime", room.getEndTime() != null ? room.getEndTime().toString() : "");
        m.put("sellerID", room.getSellerID());

        return m;
    }

    private AuctionStatus parseAuctionStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }

        try {
            return AuctionStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return switch (statusStr) {
                case "ĐANG CHẠY", "RUNNING" -> AuctionStatus.RUNNING;
                case "SẮP MỞ", "OPEN" -> AuctionStatus.OPEN;
                case "KẾT THÚC", "FINISHED" -> AuctionStatus.FINISHED;
                case "ĐÃ THANH TOÁN", "PAID" -> AuctionStatus.PAID;
                case "ĐÃ HỦY", "CANCELED" -> AuctionStatus.CANCELED;
                default -> null;
            };
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonPayload(MessageDTO request) {
        if (request == null || request.getPayload() == null || request.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("Payload không được để trống.");
        }

        String payload = request.getPayload().trim();

        if (!payload.startsWith("{")) {
            throw new IllegalArgumentException("Payload phải là JSON object.");
        }

        try {
            Map<String, Object> data = gson.fromJson(payload, Map.class);

            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("Payload JSON không hợp lệ.");
            }

            return data;

        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Payload JSON sai định dạng.");
        }
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);

        if (value == null) {
            return defaultValue;
        }

        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private int getInt(Map<String, Object> data, String key) {
        Object value = data.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số nguyên.");
        }
    }

    private long getLong(Map<String, Object> data, String key) {
        Object value = data.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        try {
            return (long) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số.");
        }
    }

    private BigDecimal getBigDecimal(Map<String, Object> data, String key) {
        Object value = data.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số hợp lệ.");
        }
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}