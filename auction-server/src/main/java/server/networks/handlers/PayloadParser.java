package server.networks.handlers;

import java.math.BigDecimal;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import server.networks.dto.MessageDTO;

/**
 * Utility tĩnh dùng chung để parse và validate dữ liệu trong {@link MessageDTO} payload.
 *
 * <p>Tập trung ba nhóm chức năng:
 *
 * <ol>
 *   <li>Parse raw payload (JSON hoặc legacy colon-format) thành Map / primitive.
 *   <li>Trích xuất giá trị có kiểu từ Map: {@code getInt}, {@code getLong},
 *       {@code getBigDecimal}, {@code getString}.
 *   <li>Làm sạch chuỗi số tiền: xử lý ký hiệu VND, dấu chấm/phẩy nhóm, và scientific notation.
 * </ol>
 */
public final class PayloadParser {

    private static final Gson GSON = new Gson();
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private PayloadParser() {
        // utility class — không cho khởi tạo
    }

    // ─── 1. Parse payload thành Map hoặc primitive ────────────────────────────

    /** Lấy payload từ request, ném {@link IllegalArgumentException} nếu rỗng. */
    public static String requirePayload(MessageDTO request) {
        if (request == null
                || request.getPayload() == null
                || request.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("Payload không được để trống.");
        }
        return request.getPayload().trim();
    }

    /** Parse payload JSON thành {@code Map<String, Object>}. Payload phải bắt đầu bằng {@code '{'}. */
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
     * Parse payload thành ID kiểu {@code long}.
     *
     * <p>Hỗ trợ cả JSON object ({@code {"roomId":1}} hoặc {@code {"auctionId":1}}) lẫn plain
     * number string. Tự động fallback giữa {@code roomId} và {@code auctionId}.
     *
     * @param payload raw payload string
     * @param jsonKey tên field chính (vd {@code "roomId"})
     */
    public static long parseIdPayload(String payload, String jsonKey) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu " + jsonKey + ".");
        }

        String raw = payload.trim();
        if (!raw.startsWith("{")) {
            return toLong(raw);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = GSON.fromJson(raw, Map.class);
            Object value = data != null ? data.get(jsonKey) : null;

            if (value == null && "roomId".equals(jsonKey)) {
                value = data != null ? data.get("auctionId") : null;
            }
            if (value == null && "auctionId".equals(jsonKey)) {
                value = data != null ? data.get("roomId") : null;
            }
            if (value == null) {
                throw new IllegalArgumentException("Thiếu " + jsonKey + ".");
            }
            return toLong(value);

        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Payload JSON sai định dạng.");
        }
    }

    // ─── 2. Extract typed values từ Map ───────────────────────────────────────

    public static boolean hasKey(Map<String, Object> data, String key) {
        return data != null && data.containsKey(key) && data.get(key) != null;
    }

    public static String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
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

    /** Lấy {@link BigDecimal} dương từ Map. Ném nếu thiếu, không parse được, hoặc {@code <= 0}. */
    public static BigDecimal getBigDecimal(Map<String, Object> data, String key) {
        BigDecimal value = getBigDecimalAllowNegative(data, key);
        validatePositive(value, key);
        return value;
    }

    /** Lấy {@link BigDecimal} từ Map, cho phép giá trị âm hoặc 0 (vd: delta điều chỉnh ví). */
    public static BigDecimal getBigDecimalAllowNegative(Map<String, Object> data, String key) {
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

    // ─── 3. Validation ───────────────────────────────────────────────────────

    public static void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " phải lớn hơn 0.");
        }
    }

    // ─── 4. Làm sạch chuỗi số ─────────────────────────────────────────────────

    /**
     * Chuyển chuỗi số tiền về dạng có thể parse thành {@link BigDecimal}.
     *
     * <p>Hỗ trợ các định dạng:
     *
     * <ul>
     *   <li>{@code "5000000.0"} → {@code "5000000.0"} (Gson serialize double)
     *   <li>{@code "5.000.000"} → {@code "5000000"} (VN grouping)
     *   <li>{@code "5,000,000"} → {@code "5000000"} (EN grouping)
     *   <li>{@code "5.000.000,50"} → {@code "5000000.50"} (VN có decimal)
     *   <li>{@code "5,000,000.50"} → {@code "5000000.50"} (EN có decimal)
     *   <li>{@code "5.0E7"} → {@code "50000000"} (scientific notation)
     * </ul>
     */
    public static String cleanNumberText(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text
                .replace("đ", "")
                .replace("VND", "")
                .replace("VNĐ", "")
                .replace(" ", "")
                .trim();

        if (cleaned.isEmpty()) {
            return "";
        }

        // Scientific notation — chuyển sang plain string trước khi xử lý separator.
        if (cleaned.matches("^-?\\d+(\\.\\d+)?[eE]-?\\d+$")) {
            try {
                return new BigDecimal(cleaned).toPlainString();
            } catch (NumberFormatException ignored) {
                // fallback xuống logic xử lý separator bên dưới
            }
        }

        boolean hasComma = cleaned.contains(",");
        boolean hasDot = cleaned.contains(".");

        if (hasComma && hasDot) {
            // Có cả hai → dấu xuất hiện sau cùng là decimal, dấu còn lại là grouping.
            int lastComma = cleaned.lastIndexOf(',');
            int lastDot = cleaned.lastIndexOf('.');
            if (lastDot > lastComma) {
                cleaned = cleaned.replace(",", "");
            } else {
                cleaned = cleaned.replace(".", "").replace(',', '.');
            }
        } else if (hasComma) {
            // Chỉ có "," — decimal khi xuất hiện đúng 1 lần và sau nó 1-2 chữ số.
            int count = cleaned.length() - cleaned.replace(",", "").length();
            int afterLen = cleaned.length() - cleaned.lastIndexOf(',') - 1;
            boolean isGrouping = cleaned.matches(".*,\\d{3}(?!\\d).*");
            if (count == 1 && afterLen >= 1 && afterLen <= 2 && !isGrouping) {
                cleaned = cleaned.replace(',', '.');
            } else {
                cleaned = cleaned.replace(",", "");
            }
        } else if (hasDot) {
            // Chỉ có "." — grouping khi >1 chấm hoặc đúng 3 chữ số sau chấm duy nhất.
            int count = cleaned.length() - cleaned.replace(".", "").length();
            int afterLen = cleaned.length() - cleaned.lastIndexOf('.') - 1;
            if (count > 1 || afterLen == 3) {
                cleaned = cleaned.replace(".", "");
            }
        }

        return cleaned.replaceAll("[^0-9.\\-]", "").trim();
    }

    /** Phiên bản đơn giản cho số nguyên — xóa hết dấu phẩy, dấu chấm, ký hiệu VND. */
    public static String cleanIntegerText(String text) {
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

    // ─── 5. Internal helpers ─────────────────────────────────────────────────

    /**
     * Chuyển Object về {@code long}. Xử lý cả {@link Number} lẫn String, kể cả khi Gson
     * serialize số nguyên thành chuỗi dạng {@code "1.0"}.
     */
    public static long toLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Giá trị số không được null.");
        }
        if (value instanceof Number n) {
            return n.longValue();
        }

        try {
            String raw = String.valueOf(value).trim();
            if (raw.matches("-?\\d+\\.0+")) {
                raw = raw.substring(0, raw.indexOf('.'));
            }
            return Long.parseLong(cleanIntegerText(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Giá trị phải là số nguyên.");
        }
    }
}