package client.utils.dialogs;

import client.models.user.UserSession;
import client.services.RequestResponse;
import client.services.ServerGateway;
import client.utils.MoneyFormatter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * WalletDialogs — dialog liên quan đến ví: nạp tiền, thông báo điều chỉnh ví.
 *
 * <p>Tách từ {@code AuctionListController} (gốc ~250 dòng inline-style HBox/VBox).
 */
public final class WalletDialogs {

    private WalletDialogs() {}

    // ─── Deposit dialog ─────────────────────────────────────────────

    /**
     * Hiển thị dialog nạp tiền, gửi request và gọi {@code onBalanceUpdated}
     * sau khi server trả về SUCCESS với số dư mới.
     *
     * @param parentCss optional URL của stylesheet để áp dụng vào popup
     */
    public static void showDepositDialog(java.net.URL parentCss, DoubleConsumer onBalanceUpdated) {
        double curBalance = UserSession.getInstance().getBalance();

        // ─── Header ─────────────────────────────────────────────────
        Label iconLbl = new Label("💳");
        iconLbl.setStyle("-fx-font-size:26px;");
        StackPane iconWrap = new StackPane(iconLbl);
        iconWrap.setStyle("-fx-background-color:linear-gradient(to bottom right,#10B981,#059669);"
                + "-fx-background-radius:18; -fx-min-width:54; -fx-max-width:54; -fx-min-height:54; -fx-max-height:54;"
                + "-fx-effect:dropshadow(three-pass-box,rgba(16,185,129,0.38),16,0,0,5);");
        Label lblTitle = StyledComponents.bold("Nạp tiền vào ví", "22px", "#0F172A");
        Label lblSub   = StyledComponents.sub("Số dư sẽ được cộng ngay lập tức sau khi nạp.");
        VBox headerText = new VBox(4, lblTitle, lblSub);
        HBox header = new HBox(16, iconWrap, headerText);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:linear-gradient(to bottom right,#F8FAFC,#ECFDF5);"
                + "-fx-padding:22 24 20 24; -fx-border-color:transparent transparent #E2E8F0 transparent; -fx-border-width:0 0 1 0;");

        // ─── Balance cards ──────────────────────────────────────────
        Label lblBalanceVal = StyledComponents.bold(MoneyFormatter.format(curBalance), "28px", "#059669");
        VBox balanceCard = StyledComponents.balanceCard("SỐ DƯ HIỆN TẠI", lblBalanceVal, "#FFFFFF", "#A7F3D0");

        Label lblAfterVal = StyledComponents.bold(MoneyFormatter.format(curBalance), "22px", "#2563EB");
        VBox afterCard = StyledComponents.balanceCard("SAU KHI NẠP", lblAfterVal, "#FFFFFF", "#BFDBFE");

        HBox cardsRow = StyledComponents.stretch(12, balanceCard, afterCard);

        // ─── Amount input ───────────────────────────────────────────
        Label lblAmountHint = StyledComponents.bold("Số tiền nạp (VNĐ)", "13px", "#334155");
        TextField txtAmount = new TextField();
        txtAmount.setPromptText("Nhập số tiền...");
        txtAmount.setPrefHeight(52);
        txtAmount.setStyle("-fx-font-size:20px; -fx-font-weight:900; -fx-background-color:#F8FAFC;"
                + "-fx-border-color:#CBD5E1; -fx-background-radius:14; -fx-border-radius:14;"
                + "-fx-padding:10 16; -fx-text-fill:#0F172A; -fx-prompt-text-fill:#94A3B8;");

        Label lblFormatted = new Label(" ");
        lblFormatted.setStyle("-fx-font-size:13px; -fx-font-weight:800; -fx-text-fill:#059669;");
        Label lblError = new Label();
        lblError.setStyle("-fx-font-size:13px; -fx-font-weight:800; -fx-text-fill:#EF4444;");

        txtAmount.textProperty().addListener((obs, old, newVal) -> {
            if (!newVal.matches("\\d*")) { txtAmount.setText(newVal.replaceAll("[^\\d]", "")); return; }
            lblError.setText("");
            long add = newVal.isEmpty() ? 0 : Long.parseLong(newVal);
            lblFormatted.setText(newVal.isEmpty() ? " " : MoneyFormatter.format(add));
            lblAfterVal.setText(MoneyFormatter.format(curBalance + add));
        });

        // ─── Quick amount buttons ───────────────────────────────────
        String[] labels  = {"+500k", "+1tr", "+2tr", "+5tr", "+10tr", "+50tr"};
        long[]   amounts = {500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 50_000_000};
        HBox quickRow = new HBox(8);
        quickRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < labels.length; i++) {
            final long val = amounts[i];
            Button btn = new Button(labels[i]);
            btn.setStyle("-fx-background-color:#ECFDF5; -fx-text-fill:#047857; -fx-border-color:#A7F3D0;"
                    + "-fx-border-radius:999; -fx-background-radius:999; -fx-font-weight:900;"
                    + "-fx-font-size:12px; -fx-padding:7 13; -fx-cursor:hand;");
            btn.setOnAction(e -> {
                long cur = txtAmount.getText().trim().isEmpty() ? 0 : Long.parseLong(txtAmount.getText().trim());
                txtAmount.setText(String.valueOf(cur + val));
            });
            quickRow.getChildren().add(btn);
        }

        VBox inputSection = new VBox(8, lblAmountHint, txtAmount, lblFormatted, quickRow, lblError);
        VBox body = new VBox(16, cardsRow, inputSection);
        body.setStyle("-fx-padding:22 24;");

        // ─── Footer ─────────────────────────────────────────────────
        Button btnCancel = new Button("Hủy");
        btnCancel.setStyle("-fx-background-color:#FFFFFF; -fx-border-color:#CBD5E1; -fx-border-radius:14;"
                + "-fx-background-radius:14; -fx-text-fill:#475569; -fx-font-weight:900; -fx-font-size:14px; -fx-padding:12 24; -fx-cursor:hand;");
        Button btnSubmit = new Button("💰  Nạp ngay");
        btnSubmit.setStyle("-fx-background-color:linear-gradient(to right,#10B981,#059669); -fx-text-fill:white;"
                + "-fx-font-weight:900; -fx-font-size:14px; -fx-background-radius:14; -fx-padding:12 28; -fx-cursor:hand;"
                + "-fx-effect:dropshadow(three-pass-box,rgba(16,185,129,0.32),14,0,0,5);");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, spacer, btnCancel, btnSubmit);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-background-color:#F8FAFC; -fx-padding:16 24 20 24;"
                + "-fx-border-color:#E2E8F0 transparent transparent transparent; -fx-border-width:1 0 0 0;");

        // ─── Root ───────────────────────────────────────────────────
        VBox root = new VBox(header, body, footer);
        root.setStyle("-fx-background-color:white; -fx-background-radius:24; -fx-border-radius:24;"
                + "-fx-border-color:#E2E8F0; -fx-effect:dropshadow(three-pass-box,rgba(15,23,42,0.18),34,0.16,0,14);");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        if (parentCss != null) scene.getStylesheets().add(parentCss.toExternalForm());

        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initStyle(StageStyle.TRANSPARENT);
        popup.setTitle("Nạp tiền vào ví");
        popup.setScene(scene);
        popup.setWidth(500);
        popup.setResizable(false);

        btnCancel.setOnAction(e -> popup.close());
        btnSubmit.setOnAction(e -> {
            String text = txtAmount.getText().trim();
            if (text.isEmpty()) { lblError.setText("❌ Vui lòng nhập số tiền!"); return; }
            double amount;
            try { amount = Double.parseDouble(text); }
            catch (NumberFormatException ex) { lblError.setText("❌ Dữ liệu không hợp lệ!"); return; }
            if (amount <= 0)            { lblError.setText("❌ Số tiền phải lớn hơn 0!"); return; }
            if (amount > 1_000_000_000) { lblError.setText("❌ Tối đa 1.000.000.000 đ mỗi lần nạp!"); return; }
            popup.close();
            executeDeposit(amount, onBalanceUpdated);
        });
        txtAmount.setOnAction(e -> btnSubmit.fire());
        popup.show();
        Platform.runLater(txtAmount::requestFocus);
    }

    private static void executeDeposit(double amount, DoubleConsumer onBalanceUpdated) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount);

        RequestResponse.exchange()
                .request("DEPOSIT", new com.google.gson.Gson().toJson(data))
                .onSuccess(payload -> {
                    Double newBal = null;
                    try {
                        Map<?, ?> result = new com.google.gson.Gson().fromJson(payload, Map.class);
                        Object nb = result == null ? null : result.get("newBalance");
                        if (nb instanceof Number n) newBal = n.doubleValue();
                    } catch (Exception ignored) {}
                    if (newBal != null) {
                        UserSession.getInstance().setBalance(newBal);
                        if (onBalanceUpdated != null) onBalanceUpdated.accept(newBal);
                    }
                    Dialogs.info("Nạp tiền thành công",
                            "✅ Đã nạp " + MoneyFormatter.format(amount) + " vào ví!\n\nSố dư đã được cập nhật.");
                })
                .onFailed(payload -> Dialogs.error("Lỗi", "❌ Nạp tiền thất bại!\n\n" + payload))
                .onTimeout(() -> Dialogs.error("Hết thời gian chờ", "Server không phản hồi. Vui lòng thử lại."))
                .send();
    }

    // ─── Wallet adjusted notification (Bidder nhận từ Admin) ────────

    public static void showWalletAdjusted(String title, String message, double delta,
                                          double newBalance, String reason) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông báo ví");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        boolean isPlus = delta >= 0;

        Label iconLbl = new Label(isPlus ? "💰" : "⚠️");
        iconLbl.setStyle("-fx-font-size:34px; -fx-background-color:" + (isPlus ? "#DCFCE7" : "#FEE2E2")
                + "; -fx-background-radius:999; -fx-min-width:64; -fx-min-height:64; -fx-alignment:center;");
        Label lblTitle   = StyledComponents.bold(title, "20px", "#0F172A");
        lblTitle.setWrapText(true);
        Label lblMessage = StyledComponents.bold(message, "14px", "#475569");
        lblMessage.setWrapText(true);
        HBox header = new HBox(16, iconLbl, new VBox(6, lblTitle, lblMessage));
        header.setAlignment(Pos.CENTER_LEFT);

        Label deltaVal = StyledComponents.bold(
                (isPlus ? "+" : "-") + MoneyFormatter.format(Math.abs(delta)),
                "24px", isPlus ? "#059669" : "#DC2626");
        VBox deltaCard = StyledComponents.balanceCard("BIẾN ĐỘNG", deltaVal,
                isPlus ? "#ECFDF5" : "#FEF2F2",
                isPlus ? "#A7F3D0" : "#FECACA");

        Label balVal = StyledComponents.bold(MoneyFormatter.format(newBalance), "24px", "#2563EB");
        VBox balCard = StyledComponents.balanceCard("SỐ DƯ MỚI", balVal, "#EFF6FF", "#BFDBFE");

        HBox cards = StyledComponents.stretch(12, deltaCard, balCard);

        VBox body = new VBox(18, header, cards);
        if (reason != null && !reason.isBlank() && !"null".equalsIgnoreCase(reason)) {
            Label reasonTitle = StyledComponents.bold("LÝ DO ĐIỀU CHỈNH", "11px", "#64748B");
            Label reasonVal = new Label(reason);
            reasonVal.setWrapText(true);
            reasonVal.setStyle("-fx-font-size:13px; -fx-text-fill:#334155; -fx-background-color:#F8FAFC;"
                    + "-fx-background-radius:14; -fx-border-color:#E2E8F0; -fx-border-radius:14; -fx-padding:12 14;");
            body.getChildren().addAll(reasonTitle, reasonVal);
        }
        body.setPadding(new Insets(22));
        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().setPrefWidth(520);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        StyledComponents.styleDialogButton(okBtn, "Đã hiểu",
                "linear-gradient(to right,#2563EB,#7C3AED)", "white");
        dialog.show();
    }

    // ─── Convenience overload not needing parent CSS  ───────────────

    public static void showDepositDialog(DoubleConsumer onBalanceUpdated) {
        showDepositDialog(null, onBalanceUpdated);
    }

    // Suppress unused import warnings (ServerGateway re-used elsewhere)
    @SuppressWarnings("unused")
    private static void touch(ServerGateway gw, Consumer<String> c, Alert.AlertType t) {}
}
