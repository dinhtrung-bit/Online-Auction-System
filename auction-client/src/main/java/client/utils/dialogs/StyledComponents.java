package client.utils.dialogs;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * StyledComponents — builder chung cho Label / Button / Card / TextField
 * với inline-style đã chuẩn hoá thành hằng số.
 *
 * <p>Gom toàn bộ chuỗi inline-style trùng lặp giữa các controllers
 * (AdminDashboard adjust-balance, AuctionList deposit, etc.) vào một nơi.
 */
public final class StyledComponents {

    // ─── Style hằng số ──────────────────────────────────────────────
    public static final String LABEL_CARD_TITLE =
            "-fx-font-size: 11px; -fx-font-weight: 900; -fx-text-fill: #64748B;";

    private StyledComponents() {}

    // ─── Label builders ─────────────────────────────────────────────

    public static String boldStyle(String size, String color) {
        return "-fx-font-size:" + size + "; -fx-font-weight:900; -fx-text-fill:" + color + ";";
    }

    public static Label bold(String text, String size, String color) {
        Label lbl = new Label(text);
        lbl.setStyle(boldStyle(size, color));
        return lbl;
    }

    public static Label sub(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-size:13px; -fx-text-fill:#64748B;");
        return lbl;
    }

    public static Label icon(String emoji) {
        Label lbl = new Label(emoji);
        lbl.setStyle("-fx-font-size: 34px; -fx-background-color: linear-gradient(to bottom right,#DBEAFE,#EDE9FE);"
                + "-fx-background-radius:999; -fx-min-width:68; -fx-min-height:68; -fx-alignment:center;");
        return lbl;
    }

    public static Label badge(String text, String textColor, String bgColor) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:11px; -fx-font-weight:900; -fx-text-fill:" + textColor
                + "; -fx-background-color:" + bgColor + "; -fx-background-radius:999; -fx-padding:5 10;");
        return lbl;
    }

    // ─── Card builders ──────────────────────────────────────────────

    /** Card label–value (CARD_LABEL trên, value Label tuỳ ý dưới). */
    public static VBox balanceCard(String titleText, Label valueLabel, String bg, String border) {
        Label title = new Label(titleText);
        title.setStyle(LABEL_CARD_TITLE);
        VBox card = new VBox(5, title, valueLabel);
        card.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:18;"
                + "-fx-border-color:" + border + "; -fx-border-radius:18; -fx-padding:16;");
        return card;
    }

    /** Bọc VBox bên trong style card chuẩn. */
    public static VBox card(String bg, String border, VBox inner) {
        inner.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:18;"
                + "-fx-border-color:" + border + "; -fx-border-radius:18; -fx-padding:16;");
        return inner;
    }

    /** HBox với VBox children giãn đều. */
    public static HBox stretch(int spacing, VBox... nodes) {
        HBox box = new HBox(spacing, nodes);
        for (VBox n : nodes) {
            HBox.setHgrow(n, Priority.ALWAYS);
            n.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    // ─── Input ──────────────────────────────────────────────────────

    public static TextField styledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefHeight(42);
        tf.setStyle("-fx-background-color:white; -fx-background-radius:12;"
                + "-fx-border-color:#CBD5E1; -fx-border-radius:12;"
                + "-fx-padding:0 14; -fx-font-size:14px; -fx-font-weight:700;");
        return tf;
    }

    /** TextArea chỉ đọc dùng trong dialog/alert. */
    public static TextArea readonlyArea(String text, double prefHeight) {
        TextArea area = new TextArea(text == null ? "" : text);
        area.setWrapText(true);
        area.setEditable(false);
        area.setPrefHeight(prefHeight);
        area.getStyleClass().add("readonly-area");
        return area;
    }

    // ─── Dialog button styling ──────────────────────────────────────

    public static void styleDialogButton(Button btn, String text, String bg, String fg) {
        if (btn == null) return;
        btn.setText(text);
        btn.setStyle("-fx-background-color:" + bg + "; -fx-text-fill:" + fg
                + "; -fx-font-weight:900; -fx-background-radius:12; -fx-padding:10 22; -fx-cursor:hand;");
    }

    // ─── Grid row helper ────────────────────────────────────────────

    /** Thêm 1 hàng label–value vào GridPane. */
    public static void addInfoRow(GridPane grid, int row, String label, String value) {
        Label left = new Label(label);
        left.getStyleClass().add("spec-label");
        Label right = new Label(value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? "--" : value);
        right.setWrapText(true);
        right.getStyleClass().add("spec-value");
        grid.add(left, 0, row);
        grid.add(right, 1, row);
    }

    // ─── Dialog wrappers ────────────────────────────────────────────

    /** Hiển thị Dialog tuỳ chỉnh có ScrollPane. */
    public static void showScrollable(String title, VBox content, double width, double height) {
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
}
