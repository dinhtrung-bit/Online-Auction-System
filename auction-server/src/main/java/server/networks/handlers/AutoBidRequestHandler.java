package server.networks.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import server.models.users.Bidder;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.AuctionService;

import java.math.BigDecimal;
import java.util.Map;

public class AutoBidRequestHandler {

    private final AuctionService auctionService;
    private final Gson gson = new Gson();

    public AutoBidRequestHandler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public MessageDTO handleSetAutoBid(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Bidder bidder)) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Chỉ Bidder mới được đặt auto-bid!");
        }

        try {
            Map<String, Object> data = parseJsonPayload(request);

            int auctionId = getInt(data, "auctionId");
            BigDecimal maxBid = getBigDecimal(data, "maxBid");
            BigDecimal step = getBigDecimal(data, "step");

            auctionService.registerAutoBid(auctionId, bidder, maxBid, step);

            return new MessageDTO("SET_AUTO_BID_SUCCESS", "Đặt auto bid thành công!");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("SET_AUTO_BID_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleCancelAutoBid(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Chưa đăng nhập.");
        }

        try {
            int auctionId = parseAuctionId(request);
            int userId = loggedInUser.getUserId();

            auctionService.cancelAutoBid(auctionId, userId);

            return new MessageDTO("CANCEL_AUTO_BID_SUCCESS", "Hủy auto bid thành công!");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Lỗi hệ thống: " + e.getMessage());
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

    private int parseAuctionId(MessageDTO request) {
        if (request == null || request.getPayload() == null || request.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu auctionId.");
        }

        String payload = request.getPayload().trim();

        try {
            if (payload.startsWith("{")) {
                Map<String, Object> data = parseJsonPayload(request);
                return getInt(data, "auctionId");
            }

            return (int) Double.parseDouble(payload);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("auctionId phải là số nguyên.");
        }
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

    private BigDecimal getBigDecimal(Map<String, Object> data, String key) {
        Object value = data.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        try {
            BigDecimal number = new BigDecimal(String.valueOf(value));

            if (number.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(key + " phải lớn hơn 0.");
            }

            return number;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số hợp lệ.");
        }
    }
}