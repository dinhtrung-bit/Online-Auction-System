package server.networks.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import server.networks.dto.MessageDTO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * PayloadParser — Utility dùng chung để đọc và trích xuất dữ liệu từ MessageDTO payload.
 *
 * Tập trung 3 nhóm chức năng:
 *   1. Parse raw payload (JSON hoặc legacy colon-format) thành Map / primitive
 *   2. Extract typed values từ Map (getInt, getLong, getBigDecimal, getString...)
 *   3. Làm sạch chuỗi số tiền (cleanNumberText) — xử lý VND, dấu chấm/phẩy, scientific
 *
 * Tất cả method đều là static — không cần khởi tạo instance.
 */
public final class PayloadParser {

    private static final Gson GSON = new Gson();

    private PayloadParser() {}

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Parse payload thành Map hoặc trích xuất primitive
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy payload từ request, ném IllegalArgumentException nếu rỗng.
     */
    public static String requirePayload(MessageDTO request) {
        if (request == null
                || request.getPayload() == null
                || request.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("Payload không được để trống.");
        }
        return request.getPayload().trim();
    }

    /**
     * Parse payload JSON thành Map. Payload phải bắt đầu bằng '{'.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJsonPayload(MessageDTO request) {
        String payload = requirePayload(request);

        if (!payload.startsWith("{")) {
            throw new IllegalArgumentException("Payload phải là JSON object.");
        }

        try {
            Map<String, Object> data = GSON.fromJson(payload, Map.class);
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("Payload JSON không hợp lệ.");
            }
            return data;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Payload JSON sai định dạng.");
        }
    }

    /**
     * Parse payload thành long ID.
     * Hỗ trợ cả JSON object ({"roomId":1} / {"auctionId":1}) lẫn plain number string.
     *
     * @param payload  raw payload string
     * @param jsonKey  tên field chính (vd "roomId"), tự fallback sang "auctionId"/"roomId"
     */
    public static long parseIdPayload(String payload, String jsonKey) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu " + jsonKey + ".");
        }

        String raw = payload.trim();

        if (raw.startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = GSON.fromJson(raw, Map.class);
                Object value = data != null ? data.get(jsonKey) : null;

                if (value == null && "roomId".equals(jsonKey))    value = data != null ? data.get("auctionId") : null;
                if (value == null && "auctionId".equals(jsonKey)) value = data != null ? data.get("roomId")    : null;

                if (value == null) {
                    throw new IllegalArgumentException("Thiếu " + jsonKey + ".");
                }
                return toLong(value);
            } catch (JsonSyntaxException e) {
                throw new IllegalArgumentException("Payload JSON sai định dạng.");
            }
        }

        return toLong(raw);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Extract typed values từ Map
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean hasKey(Map<String, Object> data, String key) {
        return data != null && data.containsKey(key) && data.get(key) != null;
    }

    public static String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    public static int getInt(Map<String, Object> data, String key) {
        return (int) getLong(data, key);
    }

    public static long getLong(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }
        return toLong(value);
    }

    public static BigDecimal getBigDecimal(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }
        try {
            return new BigDecimal(cleanNumberText(String.valueOf(value)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số hợp lệ.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Làm sạch chuỗi số
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Chuyển chuỗi số tiền (có thể có ký hiệu VND, dấu chấm/phẩy nhóm, hay
     * scientific notation) về dạng BigDecimal-parseable.
     *
     * Ví dụ:
     *   "5000000.0"   → "5000000.0"  (Gson double)
     *   "5.000.000"   → "5000000"    (VN grouping)
     *   "5,000,000"   → "5000000"    (EN grouping)
     *   "5.000.000,50"→ "5000000.50" (VN với decimal)
     *   "5,000,000.50"→ "5000000.50" (EN với decimal)
     *   "5.0E7"       → "50000000"   (scientific notation)
     */
    public static String cleanNumberText(String text) {
        if (text == null) return "";

        String cleaned = text
                .replace("đ", "")
                .replace("VND", "")
                .replace("VNĐ", "")
                .replace(" ", "")
                .trim();

        if (cleaned.isEmpty()) return "";

        // Scientific notation (vd "5.0E7") — chuyển sang plain string trước khi xử lý separator
        if (cleaned.matches("^-?\\d+(\\.\\d+)?[eE]-?\\d+$")) {
            try {
                return new BigDecimal(cleaned).toPlainString();
            } catch (NumberFormatException ignored) {
                // fallback xuống logic dưới
            }
        }

        boolean hasComma = cleaned.contains(",");
        boolean hasDot   = cleaned.contains(".");

        if (hasComma && hasDot) {
            // Có cả hai → dấu xuất hiện sau cùng là decimal, dấu còn lại là grouping
            int lastComma = cleaned.lastIndexOf(',');
            int lastDot   = cleaned.lastIndexOf('.');
            if (lastDot > lastComma) {
                cleaned = cleaned.replace(",", "");          // "," là grouping
            } else {
                cleaned = cleaned.replace(".", "")           // "." là grouping
                        .replace(',', '.');         // "," là decimal → đổi sang "."
            }
        } else if (hasComma) {
            // Chỉ có "," — decimal khi xuất hiện đúng 1 lần và sau nó 1–2 chữ số
            int count = cleaned.length() - cleaned.replace(",", "").length();
            int afterLen = cleaned.length() - cleaned.lastIndexOf(',') - 1;
            boolean isGrouping = cleaned.matches(".*,\\d{3}(?!\\d).*");
            if (count == 1 && afterLen >= 1 && afterLen <= 2 && !isGrouping) {
                cleaned = cleaned.replace(',', '.');
            } else {
                cleaned = cleaned.replace(",", "");
            }
        } else if (hasDot) {
            // Chỉ có "." — grouping khi có nhiều hơn 1 chấm, hoặc đúng 3 chữ số sau chấm duy nhất
            int count    = cleaned.length() - cleaned.replace(".", "").length();
            int afterLen = cleaned.length() - cleaned.lastIndexOf('.') - 1;
            if (count > 1 || afterLen == 3) {
                cleaned = cleaned.replace(".", "");
            }
            // else: giữ nguyên (vd "5000000.0" hay "1.5")
        }

        return cleaned.replaceAll("[^0-9.\\-]", "").trim();
    }

    /**
     * Làm sạch chuỗi để parse thành long/int (không giữ dấu chấm thập phân).
     */
    public static String cleanIntegerText(String text) {
        if (text == null) return "";
        return text
                .replace("đ", "")
                .replace("VND", "")
                .replace("VNĐ", "")
                .replace(",", "")
                .replace(".", "")
                .replaceAll("[^0-9\\-]", "")
                .trim();
    }

    /**
     * Kiểm tra và validate giá trị BigDecimal > 0.
     */
    public static void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " phải lớn hơn 0.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static long toLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Giá trị số không được null.");
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String raw = String.valueOf(value).trim();
            // Gson đôi khi serialize integer thành "1.0" — bỏ phần thập phân nếu chỉ là ".0"
            if (raw.matches("-?\\d+\\.0+")) {
                raw = raw.substring(0, raw.indexOf('.'));
            }
            return Long.parseLong(cleanIntegerText(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Giá trị phải là số nguyên.");
        }
    }
}