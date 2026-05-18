package client.utils;

import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.text.NumberFormat;

/**
 * Các tiện ích UI dùng chung cho toàn bộ controllers.
 *
 * <p>Tập trung những đoạn code lặp lại từ:
 * AdminDashboardController, AuctionDetailController,
 * AuctionListController, SellerDashboardController.
 */
public final class UiUtils {

    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private UiUtils() {}

    // ─── Tiền tệ ─────────────────────────────────────────────────────────────

    /** Định dạng số tiền VND có dấu phân cách và hậu tố "đ". */
    public static String formatMoney(double value) {
        return VND.format(Math.round(value)) + " đ";
    }

    /** Phiên bản dùng String.format (không cần NumberFormat locale). */
    public static String formatVND(double value) {
        return String.format("%,.0f đ", value).replace(",", ".");
    }

    // ─── Ngày giờ ────────────────────────────────────────────────────────────

    /**
     * Chuyển chuỗi ISO DateTime sang định dạng "HH:mm · dd/MM/yyyy".
     * Nếu không parse được, trả về chuỗi gốc (thay T bằng dấu cách).
     */
    public static String formatDateTime(String value) {
        if (value == null || value.isBlank()) return "--";
        try {
            return LocalDateTime.parse(value)
                    .format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy"));
        } catch (Exception e) {
            return value.replace('T', ' ');
        }
    }

    // ─── Trạng thái đấu giá ──────────────────────────────────────────────────

    /** Dịch mã trạng thái đấu giá sang tiếng Việt. */
    public static String statusToVietnamese(String status) {
        if (status == null) return "Không rõ";
        return switch (status.toUpperCase()) {
            case "RUNNING"  -> "Đang đấu giá";
            case "OPEN"     -> "Sắp bắt đầu";
            case "FINISHED" -> "Kết thúc";
            case "PAID"     -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            default         -> status;
        };
    }

    /** Kiểm tra trạng thái đấu giá đã kết thúc (không thể đặt giá thêm). */
    public static boolean isTerminalStatus(String status) {
        if (status == null) return false;
        return switch (status.trim().toUpperCase()) {
            case "FINISHED", "PAID", "CANCELED", "CANCELLED",
                 "KẾT THÚC", "ĐÃ THANH TOÁN", "ĐÃ HỦY" -> true;
            default -> false;
        };
    }

    // ─── Danh mục sản phẩm ───────────────────────────────────────────────────

    /** Chuẩn hoá mã danh mục sang tên tiếng Việt. */
    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "--";
        return switch (category.toUpperCase()) {
            case "ART"                       -> "Nghệ thuật";
            case "ELECTRONIC", "ELECTRONICS" -> "Đồ điện tử";
            case "VEHICLE"                   -> "Phương tiện";
            default                          -> category;
        };
    }

    // ─── Parse an toàn ───────────────────────────────────────────────────────

    /** Parse double an toàn, trả về fallback nếu thất bại. */
    public static double parseDoubleSafe(String text, double fallback) {
        try {
            if (text == null) return fallback;
            String raw = text.trim()
                    .replace("đ", "").replace("VNĐ", "").replace("VND", "").replace(" ", "");
            if (raw.matches("\\d{1,3}(\\.\\d{3})+(,\\d+)?"))
                raw = raw.replace(".", "").replace(",", ".");
            else if (raw.matches("\\d{1,3}(,\\d{3})+(\\.\\d+)?"))
                raw = raw.replace(",", "");
            else if (raw.contains(",") && !raw.contains("."))
                raw = raw.replace(",", ".");
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Parse int an toàn, trả về fallback nếu thất bại. */
    public static int parseIntSafe(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (Exception e) { return fallback; }
    }

    /** Lấy giá trị double từ Map, trả về fallback nếu null/lỗi. */
    public static double numberFrom(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(value.toString()); }
        catch (Exception e) { return fallback; }
    }

    /** Trả về chuỗi rỗng nếu value là null. */
    public static String safe(String value) {
        return value == null ? "" : value;
    }

    // ─── Dialog & Alert ──────────────────────────────────────────────────────

    /**
     * Hiển thị Alert đơn giản.
     * Nếu nội dung quá 180 ký tự, dùng TextArea có scroll thay cho contentText.
     */
    public static void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        String safeContent = (content == null || content.isBlank()) ? "Không có thông tin." : content;
        if (safeContent.length() > 180) {
            alert.getDialogPane().setContent(readonlyTextArea(safeContent, 240));
        } else {
            alert.setContentText(safeContent);
        }
        alert.show();
    }

    /** Hiển thị Dialog tuỳ chỉnh có ScrollPane. */
    public static void showCustomDialog(String title, VBox content, double width, double height) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getStyleClass().add("modern-dialog-pane");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setPrefViewportWidth(width);
        scroll.setPrefViewportHeight(height);
        scroll.getStyleClass().addAll("clean-scroll", "popup-scroll");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(width + 40);
        dialog.getDialogPane().setPrefHeight(height + 120);

        Button close = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (close != null) {
            close.setText("Đóng");
            close.getStyleClass().add("btn-primary");
        }
        dialog.showAndWait();
    }

    /** Tạo TextArea chỉ đọc dùng trong dialog/alert. */
    public static TextArea readonlyTextArea(String text, double prefHeight) {
        TextArea area = new TextArea(text == null ? "" : text);
        area.setWrapText(true);
        area.setEditable(false);
        area.setPrefHeight(prefHeight);
        area.getStyleClass().add("readonly-area");
        return area;
    }

    /** Thêm một hàng label–value vào GridPane. */
    public static void addInfoRow(GridPane grid, int row, String label, String value) {
        Label left = new Label(label);
        left.getStyleClass().add("spec-label");
        Label right = new Label(
                value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? "--" : value);
        right.setWrapText(true);
        right.getStyleClass().add("spec-value");
        grid.add(left, 0, row);
        grid.add(right, 1, row);
    }
}