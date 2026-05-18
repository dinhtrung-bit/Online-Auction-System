package client.utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * MoneyFormatter — format và parse tiền tệ VND.
 *
 * <p>Tách từ {@code UiUtils} (cũ) — gom toàn bộ logic xử lý tiền:
 * <ul>
 *   <li>{@link #format(double)} — định dạng số tiền có hậu tố " đ".
 *   <li>{@link #formatNoSuffix(long)} — định dạng số tiền không kèm hậu tố.
 *   <li>{@link #parse(String, double)} — parse linh hoạt nhiều dạng nhập.
 * </ul>
 */
public final class MoneyFormatter {

    private static final NumberFormat VN = NumberFormat.getInstance(new Locale("vi", "VN"));

    private MoneyFormatter() {}

    /** Định dạng số tiền VND có dấu phân cách và hậu tố " đ". */
    public static String format(double value) {
        return VN.format(Math.round(value)) + " đ";
    }

    /** Phiên bản dùng String.format (không cần locale). */
    public static String formatViaPattern(double value) {
        return String.format("%,.0f đ", value).replace(",", ".");
    }

    /** Định dạng số tiền không kèm hậu tố. */
    public static String formatNoSuffix(long value) {
        return VN.format(value);
    }

    /**
     * Parse số tiền nhập từ TextField — hỗ trợ nhiều format
     * (dấu chấm/phẩy thập phân, ký hiệu đ/VND, số âm).
     *
     * @throws NumberFormatException nếu chuỗi rỗng hoặc không hợp lệ.
     */
    public static double parseStrict(String text) {
        if (text == null || text.trim().isEmpty()) throw new NumberFormatException("empty");
        String raw = text.trim()
                .replace("đ", "").replace("VNĐ", "").replace("VND", "").replace(" ", "");
        if (raw.matches("-?\\d{1,3}(\\.\\d{3})+(,\\d+)?"))
            raw = raw.replace(".", "").replace(",", ".");
        else if (raw.matches("-?\\d{1,3}(,\\d{3})+(\\.\\d+)?"))
            raw = raw.replace(",", "");
        else if (raw.contains(",") && !raw.contains("."))
            raw = raw.replace(",", ".");
        raw = raw.replaceAll("[^0-9.\\-]", "");
        if (raw.isBlank() || raw.equals("-")) throw new NumberFormatException("invalid");
        return Double.parseDouble(raw);
    }

    /** Parse "safe": trả về fallback nếu thất bại. */
    public static double parseOrDefault(String text, double fallback) {
        try { return parseStrict(text); } catch (Exception e) { return fallback; }
    }
}
