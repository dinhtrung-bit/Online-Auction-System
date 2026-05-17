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

        // 1) Loại bỏ ký tự tiền tệ và khoảng trắng
        String cleaned = text
                .replace("đ", "")
                .replace("VND", "")
                .replace("VNĐ", "")
                .replace(" ", "")
                .trim();

        if (cleaned.isEmpty()) {
            return "";
        }

        // 2) Xử lý scientific notation (vd "5.0E7") bằng BigDecimal trước khi clean
        //    để tránh mất chính xác và để chuyển thành dạng plain (5.0E7 -> 50000000)
        if (cleaned.matches("^-?\\d+(\\.\\d+)?[eE]-?\\d+$")) {
            try {
                return new java.math.BigDecimal(cleaned).toPlainString();
            } catch (NumberFormatException ignored) {
                // fallback xuống dưới
            }
        }

        // 3) Xác định separator:
        //    - Nếu có CẢ dấu "," và "." -> dấu xuất hiện sau cùng là decimal,
        //      dấu còn lại là grouping (cách nhóm hàng nghìn) -> xóa.
        //    - Nếu chỉ có 1 loại dấu:
        //         * Coi là DECIMAL khi chỉ xuất hiện 1 lần và sau dấu có 1-3 chữ số (vd: 5000000.0, 1.5)
        //         * Còn lại coi là grouping (vd: 5.000.000 VN style, 5,000,000 EN style) -> xóa
        boolean hasComma = cleaned.contains(",");
        boolean hasDot = cleaned.contains(".");

        if (hasComma && hasDot) {
            int lastComma = cleaned.lastIndexOf(',');
            int lastDot = cleaned.lastIndexOf('.');
            if (lastDot > lastComma) {
                cleaned = cleaned.replace(",", "");          // "," là grouping
            } else {
                cleaned = cleaned.replace(".", "")           // "." là grouping
                        .replace(',', '.');         // "," là decimal -> đổi sang "."
            }
        } else if (hasComma) {
            int count = cleaned.length() - cleaned.replace(",", "").length();
            int lastComma = cleaned.lastIndexOf(',');
            int afterLen = cleaned.length() - lastComma - 1;
            if (count == 1 && afterLen >= 1 && afterLen <= 3
                    && !cleaned.matches(".*,\\d{3}(?!\\d).*")) {
                cleaned = cleaned.replace(',', '.');         // decimal
            } else {
                cleaned = cleaned.replace(",", "");          // grouping
            }
        } else if (hasDot) {
            int count = cleaned.length() - cleaned.replace(".", "").length();
            int lastDot = cleaned.lastIndexOf('.');
            int afterLen = cleaned.length() - lastDot - 1;
            // Nhiều hơn 1 dấu chấm -> chắc chắn là grouping (VN: "5.000.000")
            // 1 dấu chấm và phần sau đúng 3 chữ số -> coi là grouping (vd "5.000")
            // 1 dấu chấm và phần sau khác 3 chữ số -> coi là decimal (vd "5000000.0", "1.5")
            if (count > 1) {
                cleaned = cleaned.replace(".", "");
            } else if (afterLen == 3) {
                cleaned = cleaned.replace(".", "");
            }
            // còn lại giữ nguyên dấu chấm vì đó là decimal hợp lệ
        }

        // 4) Loại bỏ mọi ký tự không phải số / dấu chấm / dấu trừ
        cleaned = cleaned.replaceAll("[^0-9.\\-]", "");

        return cleaned.trim();
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