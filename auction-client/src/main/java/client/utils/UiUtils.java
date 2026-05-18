package client.utils;

import client.utils.dialogs.Dialogs;
import client.utils.dialogs.StyledComponents;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * UiUtils — facade mỏng kế thừa từ phiên bản cũ.
 *
 * <p>Refactor v2: toàn bộ logic được chuyển sang các util chuyên biệt
 * trong package {@code client.utils} và {@code client.utils.dialogs}:
 * <ul>
 *   <li>{@link MoneyFormatter} — định dạng / parse tiền tệ
 *   <li>{@link DateTimes} — định dạng ngày giờ
 *   <li>{@link StatusMapper} — trạng thái phiên / danh mục
 *   <li>{@link SafeParser} — parse số an toàn
 *   <li>{@link MapAccessor} — đọc Map<String,Object> an toàn
 *   <li>{@link Dialogs} — alert / confirm
 *   <li>{@link StyledComponents} — Label / Button / TextField builders
 * </ul>
 *
 * <p>Lý do giữ class này: tránh phải sửa hàng loạt import của các
 * file ngoài controller (model, helper khác) nếu sau này có người dùng.
 * Tất cả method ở đây chỉ là delegation — không có logic riêng.
 */
public final class UiUtils {

    private UiUtils() {}

    // ─── Money ──────────────────────────────────────────────────────
    public static String formatMoney(double v)   { return MoneyFormatter.format(v); }
    public static String formatVND(double v)     { return MoneyFormatter.formatViaPattern(v); }

    // ─── DateTime ───────────────────────────────────────────────────
    public static String formatDateTime(String iso) { return DateTimes.format(iso); }

    // ─── Status / Category ──────────────────────────────────────────
    public static String statusToVietnamese(String s)    { return StatusMapper.toVietnamese(s); }
    public static boolean isTerminalStatus(String s)     { return StatusMapper.isTerminal(s); }
    public static String normalizeCategory(String c)     { return StatusMapper.normalizeCategory(c); }

    // ─── Safe parse ─────────────────────────────────────────────────
    public static double parseDoubleSafe(String t, double fb) { return SafeParser.parseDouble(t, fb); }
    public static int    parseIntSafe(String t, int fb)       { return SafeParser.parseInt(t, fb); }
    public static double numberFrom(Object v, double fb)      { return SafeParser.numberFrom(v, fb); }
    public static String safe(String v)                       { return SafeParser.safe(v); }

    // ─── Dialog ─────────────────────────────────────────────────────
    public static void showAlert(Alert.AlertType type, String title, String content) {
        Dialogs.show(type, title, content);
    }

    public static void showCustomDialog(String title, VBox content, double width, double height) {
        StyledComponents.showScrollable(title, content, width, height);
    }

    public static TextArea readonlyTextArea(String text, double prefHeight) {
        return StyledComponents.readonlyArea(text, prefHeight);
    }

    public static void addInfoRow(GridPane grid, int row, String label, String value) {
        StyledComponents.addInfoRow(grid, row, label, value);
    }
}
