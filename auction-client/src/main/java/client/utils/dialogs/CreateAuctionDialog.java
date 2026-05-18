package client.utils.dialogs;

import client.models.item.Item;
import com.google.gson.JsonObject;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * CreateAuctionDialog — form Seller tạo phiên đấu giá mới.
 *
 * <p>Tách từ {@code SellerDashboardController.handleStartAuction} (~170 dòng inline).
 * Trả về JSON payload sẵn sàng gửi lên server hoặc {@link Optional#empty()} nếu user hủy.
 */
public final class CreateAuctionDialog {

    private CreateAuctionDialog() {}

    /**
     * Mở dialog và trả về JSON payload của phiên đấu giá nếu user xác nhận.
     */
    public static Optional<String> show(Item selectedItem) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Tạo phiên đấu giá");
        dialog.setHeaderText(null);

        ButtonType btnOk = new ButtonType("🚀 Bắt đầu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnOk, ButtonType.CANCEL);

        // ── Root ───────────────────────────────────────────────────
        VBox root = new VBox(18);
        root.setPadding(new javafx.geometry.Insets(25));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 18;");

        VBox header = buildHeader(selectedItem);
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField txtTime = new TextField(
                LocalTime.now().plusMinutes(5).format(DateTimeFormatter.ofPattern("HH:mm")));
        TextField txtDuration = new TextField("30");

        datePicker.setMaxWidth(Double.MAX_VALUE);
        txtTime.setMaxWidth(Double.MAX_VALUE);
        txtDuration.setMaxWidth(Double.MAX_VALUE);

        String inputStyle = "-fx-background-color:#f8fafc; -fx-border-color:#cbd5e1;"
                + " -fx-border-radius:10; -fx-background-radius:10; -fx-padding:10; -fx-font-size:14px;";
        datePicker.setStyle(inputStyle);
        txtTime.setStyle(inputStyle);
        txtDuration.setStyle(inputStyle);

        grid.add(buildFormLabel("Ngày bắt đầu"),     0, 0); grid.add(datePicker,  1, 0);
        grid.add(buildFormLabel("Giờ bắt đầu"),      0, 1); grid.add(txtTime,     1, 1);
        grid.add(buildFormLabel("Thời gian đấu giá"), 0, 2); grid.add(txtDuration, 1, 2);

        ColumnConstraints c1 = new ColumnConstraints(); c1.setPrefWidth(150);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPrefWidth(260); c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c1, c2);

        Label note = new Label("Gợi ý: thời gian nên đặt sau hiện tại vài phút để bidder kịp tham gia.");
        note.setWrapText(true);
        note.setStyle("-fx-background-color:#fefce8; -fx-text-fill:#854d0e;"
                + " -fx-padding:10 14; -fx-background-radius:10; -fx-font-size:12px;");

        root.getChildren().addAll(header, grid, note);

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setStyle("-fx-background-color: white; -fx-background-radius: 18;");

        StyledComponents.styleDialogButton(
                (Button) dialog.getDialogPane().lookupButton(btnOk),
                "🚀 Bắt đầu", "#10b981", "white");
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        StyledComponents.styleDialogButton(cancelBtn, "Hủy bỏ", "#f1f5f9", "#475569");

        dialog.setResultConverter(btn -> {
            if (btn != btnOk) return null;
            JsonObject payload = new JsonObject();
            payload.addProperty("itemId", selectedItem.getItemId());
            payload.addProperty("startTime", datePicker.getValue() + "T" + txtTime.getText());
            payload.addProperty("durationMinutes", txtDuration.getText());
            return payload.toString();
        });

        return dialog.showAndWait();
    }

    private static VBox buildHeader(Item selectedItem) {
        Label title = new Label("Tạo phiên đấu giá");
        title.setStyle("-fx-font-size:24px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");
        Label subTitle = new Label("Thiết lập thời gian bắt đầu và thời lượng phiên đấu giá");
        subTitle.setStyle("-fx-font-size:13px; -fx-text-fill:#64748b;");
        Label productName = new Label("📦 Sản phẩm: " + selectedItem.getName());
        productName.setStyle("-fx-background-color:#eff6ff; -fx-text-fill:#1d4ed8;"
                + " -fx-font-weight:bold; -fx-padding:10 14; -fx-background-radius:10;");
        VBox header = new VBox(6, title, subTitle, productName);
        return header;
    }

    private static Label buildFormLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight:bold; -fx-text-fill:#334155; -fx-font-size:13px;");
        return label;
    }
}
