package server.networks.handlers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Item;

/**
 * AuctionMapper — Chuyển đổi {@link AuctionRoom} thành {@code Map<String, Object>}
 * để serialize thành JSON response.
 *
 * <p>Tách ra khỏi {@link AuctionRequestHandler} để:
 *
 * <ul>
 *   <li>Dễ test riêng (không cần mock service).
 *   <li>Dùng lại ở nhiều handler nếu cần.
 *   <li>Giảm kích thước AuctionRequestHandler.
 * </ul>
 */
public final class AuctionMapper {

    private AuctionMapper() {
        // utility class
    }

    /** Map đầy đủ cho danh sách phòng đấu giá (dùng cho LIST endpoint). */
    public static Map<String, Object> toListMap(AuctionRoom room) {
        Map<String, Object> m = new LinkedHashMap<>();
        Item item = room.getItem();

        m.put("id",            room.getId());
        m.put("auctionId",     room.getId());
        m.put("itemId",        item != null ? item.getItemId() : 0);
        m.put("itemName",      item != null ? item.getName() : "N/A");
        m.put("description",   item != null ? item.getDescription() : "");
        m.put("category",      item != null ? item.getCategoryInfo() : "");
        m.put("imagePath",     item != null ? item.getImagePath() : "");
        m.put("sellerID",      room.getSellerID());
        m.put("status",        room.getStatus() != null ? room.getStatus().name() : "UNKNOWN");
        m.put("startTime",     room.getStarttime() != null ? room.getStarttime().toString() : "");
        m.put("endTime",       room.getEndTime() != null ? room.getEndTime().toString() : "");
        m.put("currentWinner",
                room.getCurrentWinner() != null ? room.getCurrentWinner().getUsername() : "Chưa có");

        m.put("startingPrice", safeDouble(item != null ? item.getStartingPrice() : null));
        m.put("bidIncrement",  safeDouble(item != null ? item.getBidIncrement() : null));
        m.put("currentPrice",
                room.getCurrentPrice() != null
                        ? room.getCurrentPrice().doubleValue()
                        : safeDouble(item != null ? item.getStartingPrice() : null));

        return m;
    }

    /** Map chi tiết cho AUCTION_DETAIL endpoint — thêm secondsLeft, bidCount. */
    public static Map<String, Object> toDetailMap(AuctionRoom room) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Item item = room.getItem();

        long secondsLeft = room.getEndTime() != null
                ? Math.max(0, Duration.between(LocalDateTime.now(), room.getEndTime()).getSeconds())
                : 0;

        BigDecimal currentPrice = room.getCurrentPrice() != null
                ? room.getCurrentPrice()
                : (item != null ? item.getStartingPrice() : BigDecimal.ZERO);

        detail.put("auctionId",     room.getId());
        detail.put("status",        room.getStatus() != null ? room.getStatus().name() : "UNKNOWN");
        detail.put("secondsLeft",   secondsLeft);
        detail.put("startTime",     room.getStarttime() != null ? room.getStarttime().toString() : "");
        detail.put("endTime",       room.getEndTime() != null ? room.getEndTime().toString() : "");
        detail.put("sellerID",      room.getSellerID());
        detail.put("currentPrice",  currentPrice.doubleValue());
        detail.put("currentWinner",
                room.getCurrentWinner() != null ? room.getCurrentWinner().getUsername() : "");
        detail.put("bidCount",      room.getBidHistory() != null ? room.getBidHistory().size() : 0);

        if (item != null) {
            detail.put("itemId",        item.getItemId());
            detail.put("itemName",      item.getName());
            detail.put("name",          item.getName());
            detail.put("description",   item.getDescription());
            detail.put("category",      item.getCategoryInfo());
            detail.put("categoryInfo",  item.getCategoryInfo());
            detail.put("imagePath",     item.getImagePath());
            detail.put("startingPrice", safeDouble(item.getStartingPrice()));
            detail.put("bidIncrement",  safeDouble(item.getBidIncrement()));
        }

        return detail;
    }

    /** Map rút gọn cho WON_AUCTIONS endpoint. */
    public static Map<String, Object> toWonMap(AuctionRoom room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auctionId",  room.getId());
        m.put("itemName",   room.getItem() != null ? room.getItem().getName() : "N/A");
        m.put("finalPrice", room.getCurrentPrice() != null ? room.getCurrentPrice().doubleValue() : 0);
        m.put("endTime",    room.getEndTime() != null ? room.getEndTime().toString() : "");
        m.put("status",     room.getStatus().name());
        return m;
    }

    /**
     * Parse {@link AuctionStatus} từ string, hỗ trợ cả tiếng Anh lẫn tiếng Việt.
     * Trả về {@code null} nếu không nhận ra (handler hiểu là "tất cả").
     */
    public static AuctionStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        String upper = statusStr.trim().toUpperCase();

        try {
            return AuctionStatus.valueOf(upper);
        } catch (IllegalArgumentException ignored) {
            // fallback theo tên tiếng Việt
        }

        return switch (upper) {
            case "ĐANG CHẠY",    "RUNNING"  -> AuctionStatus.RUNNING;
            case "SẮP MỞ",       "OPEN"     -> AuctionStatus.OPEN;
            case "KẾT THÚC",     "FINISHED" -> AuctionStatus.FINISHED;
            case "ĐÃ THANH TOÁN","PAID"     -> AuctionStatus.PAID;
            case "ĐÃ HỦY",       "CANCELED" -> AuctionStatus.CANCELED;
            default                          -> null;
        };
    }

    private static double safeDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}