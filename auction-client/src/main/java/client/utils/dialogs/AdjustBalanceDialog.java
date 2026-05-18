package client.utils.dialogs;

import client.models.user.UserViewModel;
import client.services.ServerGateway;
import client.utils.MoneyFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AdjustBalanceDialog — dialog "Điều chỉnh ví người dùng" cho Admin.
 *
 * <p>Tách từ {@code AdminDashboardController} (~120 dòng inline).
 * Khi user xác nhận, gửi {@code ADMIN_ADJUST_BALANCE} qua {@link ServerGateway}.
 */
public final class AdjustBalanceDialog {

    private AdjustBalanceDialog() {}

    public static void show(UserViewModel user) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Điều chỉnh ví người dùng");

        ButtonType okType = new ButtonType("Lưu điều chỉnh", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        double currentBalance = user.getBalance();

        // ── Header ─────────────────────────────────────────────────
        Label iconLabel  = StyledComponents.icon("💳");
        Label titleLabel = StyledComponents.bold("Điều chỉnh ví người dùng", "22px", "#0F172A");
        Label subLabel   = StyledComponents.sub("Cộng hoặc trừ tiền trực tiếp vào ví của tài khoản được chọn.");
        HBox header = new HBox(16, iconLabel, new VBox(4, titleLabel, subLabel));
        header.setAlignment(Pos.CENTER_LEFT);

        // ── User card ──────────────────────────────────────────────
        Label userBadge   = StyledComponents.badge("USER #" + user.getId(), "#2563EB", "#DBEAFE");
        Label usernameLbl = StyledComponents.bold(user.getUsername(), "20px", "#111827");
        Label roleBadge   = StyledComponents.badge(user.getRole(), "#7C3AED", "#F3E8FF");
        HBox userTop = new HBox(8, userBadge, roleBadge);
        userTop.setAlignment(Pos.CENTER_LEFT);
        VBox userCard = StyledComponents.card("#F8FAFC", "#E2E8F0", new VBox(8, userTop, usernameLbl));

        // ── Balance cards ──────────────────────────────────────────
        Label afterValue = StyledComponents.bold(MoneyFormatter.format(currentBalance), "24px", "#2563EB");
        VBox currentCard = StyledComponents.balanceCard("SỐ DƯ HIỆN TẠI",
                StyledComponents.bold(MoneyFormatter.format(currentBalance), "24px", "#059669"),
                "#ECFDF5", "#A7F3D0");
        VBox afterCard = StyledComponents.balanceCard("SỐ DƯ SAU ĐIỀU CHỈNH",
                afterValue, "#EFF6FF", "#BFDBFE");
        HBox balanceRow = StyledComponents.stretch(12, currentCard, afterCard);

        // ── Delta input ────────────────────────────────────────────
        Label deltaLabel = StyledComponents.bold("Số tiền cộng/trừ", "13px", "#334155");
        TextField txtDelta = StyledComponents.styledTextField("VD: 1000000 hoặc -500000");
        Label deltaHint = StyledComponents.sub("Nhập số dương để cộng tiền, số âm để trừ tiền.");
        Label previewLbl = StyledComponents.bold("Biến động: 0 đ", "13px", "#64748B");

        // ── Reason input ───────────────────────────────────────────
        Label reasonLabel = StyledComponents.bold("Lý do điều chỉnh", "13px", "#334155");
        TextArea txtReason = new TextArea();
        txtReason.setPromptText("Ví dụ: Cộng tiền khuyến mãi, hoàn tiền phiên bị hủy...");
        txtReason.setPrefRowCount(4);
        txtReason.setWrapText(true);

        // ── Live preview listener ──────────────────────────────────
        txtDelta.textProperty().addListener((obs, old, newVal) -> {
            try {
                double delta = MoneyFormatter.parseStrict(newVal);
                double after = currentBalance + delta;
                afterValue.setText(MoneyFormatter.format(after));
                afterValue.setStyle(StyledComponents.boldStyle("24px", after < 0 ? "#DC2626" : "#2563EB"));
                previewLbl.setText(delta > 0
                        ? "Biến động: +" + MoneyFormatter.format(delta)
                        : delta < 0
                            ? "Biến động: -" + MoneyFormatter.format(Math.abs(delta))
                            : "Biến động: 0 đ");
                previewLbl.setStyle(StyledComponents.boldStyle("13px",
                        delta > 0 ? "#059669" : delta < 0 ? "#DC2626" : "#64748B"));
            } catch (Exception e) {
                afterValue.setText("Không hợp lệ");
                afterValue.setStyle(StyledComponents.boldStyle("24px", "#DC2626"));
                previewLbl.setText("Vui lòng nhập số hợp lệ.");
                previewLbl.setStyle(StyledComponents.boldStyle("13px", "#DC2626"));
            }
        });

        VBox form = StyledComponents.card("white", "#E2E8F0",
                new VBox(10, deltaLabel, txtDelta, deltaHint, previewLbl,
                        new Separator(), reasonLabel, txtReason));
        VBox root = new VBox(18, header, userCard, balanceRow, form);
        root.setPadding(new Insets(22));
        root.setPrefWidth(620);

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefWidth(680);

        StyledComponents.styleDialogButton((Button) dialog.getDialogPane().lookupButton(okType),
                "💾 Lưu điều chỉnh",
                "linear-gradient(to right, #2563EB, #7C3AED)", "white");
        StyledComponents.styleDialogButton((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL),
                "Hủy", "#F1F5F9", "#334155");

        dialog.setResultConverter(button -> {
            if (button != okType) return null;
            try {
                double delta = MoneyFormatter.parseStrict(txtDelta.getText());
                double after = currentBalance + delta;
                if (delta == 0) {
                    Dialogs.warn("Dữ liệu không hợp lệ", "Số tiền điều chỉnh phải khác 0.");
                    return null;
                }
                if (after < 0) {
                    Dialogs.warn("Số dư không đủ", "Không thể trừ quá số dư hiện tại của người dùng.");
                    return null;
                }
                String reason = txtReason.getText() == null ? "" : txtReason.getText().trim();
                if (reason.isBlank()) reason = delta > 0 ? "Admin cộng tiền vào ví" : "Admin trừ tiền khỏi ví";

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("userId", user.getId());
                data.put("delta", delta);
                data.put("reason", reason);
                return data;
            } catch (NumberFormatException e) {
                Dialogs.error("Sai định dạng",
                        "Số tiền phải là số hợp lệ. Ví dụ: 1000000 hoặc -500000.");
                return null;
            }
        });

        dialog.showAndWait().ifPresent(data ->
                ServerGateway.send("ADMIN_ADJUST_BALANCE", data));
    }

    // Suppress unused import warnings
    @SuppressWarnings("unused")
    private static void touch(Alert a) {}
}
