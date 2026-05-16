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
            AutoBidPayload payload = parseSetAutoBidPayload(request);

            auctionService.registerAutoBid(
                    payload.auctionId,
                    bidder,
                    payload.maxBid,
                    payload.step
            );

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

    private AutoBidPayload parseSetAutoBidPayload(MessageDTO request) {
        String payload = requirePayload(request);

        // Format mới: JSON
        if (payload.startsWith("{")) {
            Map<String, Object> data = parseJsonPayload(request);

            int auctionId = hasKey(data, "auctionId")
                    ? getInt(data, "auctionId")
                    : getInt(data, "roomId");

            BigDecimal maxBid;
            if (hasKey(data, "maxBid")) {
                maxBid = getBigDecimal(data, "maxBid");
            } else if (hasKey(data, "max")) {
                maxBid = getBigDecimal(data, "max");
            } else if (hasKey(data, "amount")) {
                maxBid = getBigDecimal(data, "amount");
            } else {
                throw new IllegalArgumentException("Thiếu maxBid.");
            }

            BigDecimal step;
            if (hasKey(data, "step")) {
                step = getBigDecimal(data, "step");
            } else if (hasKey(data, "increment")) {
                step = getBigDecimal(data, "increment");
            } else if (hasKey(data, "bidIncrement")) {
                step = getBigDecimal(data, "bidIncrement");
            } else {
                step = new BigDecimal("500");
            }

            return new AutoBidPayload(auctionId, maxBid, step);
        }

        // Format cũ: auctionId:maxBid:step
        String[] parts = payload.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Payload auto-bid không hợp lệ.");
        }

        int auctionId = (int) Double.parseDouble(cleanNumberText(parts[0]));
        BigDecimal maxBid = new BigDecimal(cleanNumberText(parts[1]));

        BigDecimal step = parts.length >= 3
                ? new BigDecimal(cleanNumberText(parts[2]))
                : new BigDecimal("500");

        validatePositive(maxBid, "maxBid");
        validatePositive(step, "step");

        return new AutoBidPayload(auctionId, maxBid, step);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonPayload(MessageDTO request) {
        String payload = requirePayload(request);

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
        String payload = requirePayload(request);

        try {
            if (payload.startsWith("{")) {
                Map<String, Object> data = parseJsonPayload(request);

                if (hasKey(data, "auctionId")) {
                    return getInt(data, "auctionId");
                }

                return getInt(data, "roomId");
            }

            return (int) Double.parseDouble(cleanNumberText(payload));

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("auctionId phải là số nguyên.");
        }
    }

    private String requirePayload(MessageDTO request) {
        if (request == null || request.getPayload() == null || request.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("Payload không được để trống.");
        }

        return request.getPayload().trim();
    }

    private boolean hasKey(Map<String, Object> data, String key) {
        return data != null && data.containsKey(key) && data.get(key) != null;
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
            return (int) Double.parseDouble(cleanNumberText(String.valueOf(value)));
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
            BigDecimal number = new BigDecimal(cleanNumberText(String.valueOf(value)));
            validatePositive(number, key);
            return number;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số hợp lệ.");
        }
    }

    private void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " phải lớn hơn 0.");
        }
    }

    private String cleanNumberText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("đ", "")
                .replace("VND", "")
                .replace("VNĐ", "")
                .replace(",", "")
                .replace(".", "")
                .replaceAll("[^0-9\\-]", "")
                .trim();
    }

    private static class AutoBidPayload {
        final int auctionId;
        final BigDecimal maxBid;
        final BigDecimal step;

        AutoBidPayload(int auctionId, BigDecimal maxBid, BigDecimal step) {
            this.auctionId = auctionId;
            this.maxBid = maxBid;
            this.step = step;
        }
    }
}