package client.utils;

/**
 * SafeParser — parse số an toàn cho UI input và payload server.
 *
 * <p>Tách từ {@code UiUtils} (cũ).
 */
public final class SafeParser {

    private SafeParser() {}

    /** Parse double an toàn, trả về fallback nếu thất bại. Hỗ trợ format "1.234.567 đ". */
    public static double parseDouble(String text, double fallback) {
        return MoneyFormatter.parseOrDefault(text, fallback);
    }

    /** Parse int an toàn, trả về fallback nếu thất bại. */
    public static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Lấy giá trị double từ Object (Number, String...), trả về fallback nếu lỗi. */
    public static double numberFrom(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return value == null ? fallback : Double.parseDouble(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Trả về chuỗi rỗng nếu null. */
    public static String safe(String value) {
        return value == null ? "" : value;
    }
}
