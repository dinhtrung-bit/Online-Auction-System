package client.utils.dialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;

import java.util.Optional;

/**
 * Dialogs — entry point gọn cho alert / info / warn / error / confirm.
 *
 * <p>Tách từ {@code UiUtils.showAlert} cũ.
 * Mọi alert dài (>180 ký tự) sẽ hiển thị trong TextArea cuộn được.
 */
public final class Dialogs {

    private static final int LONG_TEXT_THRESHOLD = 180;

    private Dialogs() {}

    // ─── Cách dùng nhanh ─────────────────────────────────────────────

    public static void info(String title, String content)  { show(Alert.AlertType.INFORMATION, title, content); }
    public static void warn(String title, String content)  { show(Alert.AlertType.WARNING,     title, content); }
    public static void error(String title, String content) { show(Alert.AlertType.ERROR,       title, content); }

    /** Hiển thị confirm. Trả về true nếu user bấm OK. */
    public static boolean confirm(String title, String content) {
        Alert alert = build(Alert.AlertType.CONFIRMATION, title, content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /** Hiển thị alert tổng quát (không block). */
    public static void show(Alert.AlertType type, String title, String content) {
        build(type, title, content).show();
    }

    // ─── Internal ────────────────────────────────────────────────────

    private static Alert build(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        String safe = (content == null || content.isBlank()) ? "Không có thông tin." : content;
        if (safe.length() > LONG_TEXT_THRESHOLD) {
            TextArea area = new TextArea(safe);
            area.setWrapText(true);
            area.setEditable(false);
            area.setPrefHeight(240);
            area.setPrefWidth(520);
            area.getStyleClass().add("readonly-area");
            alert.getDialogPane().setContent(area);
        } else {
            alert.setContentText(safe);
        }
        return alert;
    }
}
