package client.controllers;

import client.models.user.UserSession;
import client.utils.MoneyFormatter;
import client.utils.dialogs.Dialogs;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;

/**
 * Controller cho AutoBid Dialog.
 *
 * <p><b>Refactor v2:</b> dùng {@link MoneyFormatter} + {@link Dialogs}.
 * Inline-style của 3 mode (STEP/FIXED/SNIPE) chuyển thành hằng số.
 */
public class AutoBidDialogController implements Initializable {

    // ─── FXML bindings ──────────────────────────────────────────────
    @FXML private ToggleButton btnModeStep, btnModeFixed, btnModeSnipe;
    @FXML private Label lblModeDesc, lblCurrentPrice, lblBalance;
    @FXML private TextField txtMaxBid, txtIncrement;
    @FXML private Slider sliderDelay;
    @FXML private Label lblDelayVal, lblNextBid, lblMaxTimes, lblTotalMax;
    @FXML private VBox panelStep, panelPreview;
    @FXML private Button btnActivate;

    // ─── State ──────────────────────────────────────────────────────
    private double currentPrice = 0;
    private String auctionId    = "";
    private BiConsumer<String, AutoBidConfig> onActivate;

    // ─── Mode constants ─────────────────────────────────────────────
    private static final String DESC_STEP  = "Tự động tăng giá mỗi khi có người vượt qua bạn, theo bước giá đã đặt.";
    private static final String DESC_FIXED = "Đặt thẳng một mức giá cố định duy nhất ngay khi kích hoạt.";
    private static final String DESC_SNIPE = "Chỉ kích hoạt khi còn dưới 60 giây — tránh bị counter bid liên tục.";

    private static final String MODE_OFF_STYLE =
            "-fx-background-color: white; -fx-text-fill: #475569;"
                    + " -fx-border-color: #CBD5E1; -fx-border-radius: 8;"
                    + " -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 0;";
    private static final String MODE_ON_STYLE =
            "-fx-background-color: #10B981; -fx-text-fill: white;"
                    + " -fx-font-weight: bold; -fx-background-radius: 8;"
                    + " -fx-cursor: hand; -fx-padding: 10 0;";

    private static final String DESC_BASE = "-fx-font-size: 12px; -fx-padding: 8 12; -fx-background-radius: 6; -fx-border-radius: 6;";

    // ─── Data class trả về ──────────────────────────────────────────
    public static class AutoBidConfig {
        public final double maxBid;
        public final double increment;
        public final int    delaySeconds;
        public final String mode; // "STEP" | "FIXED" | "SNIPE"

        public AutoBidConfig(double maxBid, double increment, int delaySeconds, String mode) {
            this.maxBid       = maxBid;
            this.increment    = increment;
            this.delaySeconds = delaySeconds;
            this.mode         = mode;
        }
    }

    // ─── Initialise ─────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sliderDelay.valueProperty().addListener((obs, old, val) -> {
            int s = (int) Math.round(val.doubleValue());
            lblDelayVal.setText(s == 0 ? "Ngay lập tức" : s + " giây");
        });

        txtMaxBid.textProperty().addListener((obs, o, n)    -> refreshPreview());
        txtIncrement.textProperty().addListener((obs, o, n) -> refreshPreview());

        btnModeStep.setSelected(true);

        lblBalance.setText("Số dư: " + MoneyFormatter.formatViaPattern(UserSession.getInstance().getBalance()));
    }

    // ─── Public API ─────────────────────────────────────────────────

    public void setup(String auctionId, double currentPrice,
                      BiConsumer<String, AutoBidConfig> onActivate) {
        this.auctionId    = auctionId;
        this.currentPrice = currentPrice;
        this.onActivate   = onActivate;
        lblCurrentPrice.setText("Giá hiện tại: " + MoneyFormatter.formatViaPattern(currentPrice));
        refreshPreview();
    }

    // ─── Mode toggle ────────────────────────────────────────────────

    @FXML
    void handleModeChange(ActionEvent event) {
        ToggleButton src = (ToggleButton) event.getSource();

        btnModeStep.setStyle(MODE_OFF_STYLE);
        btnModeFixed.setStyle(MODE_OFF_STYLE);
        btnModeSnipe.setStyle(MODE_OFF_STYLE);
        src.setStyle(MODE_ON_STYLE);
        src.setSelected(true);

        if (src == btnModeStep) {
            lblModeDesc.setText(DESC_STEP);
            lblModeDesc.setStyle(DESC_BASE + " -fx-text-fill: #064e3b;"
                    + " -fx-background-color: #F0FDF4; -fx-border-color: #A7F3D0;");
            panelStep.setVisible(true);  panelStep.setManaged(true);
        } else if (src == btnModeFixed) {
            lblModeDesc.setText(DESC_FIXED);
            lblModeDesc.setStyle(DESC_BASE + " -fx-text-fill: #1e40af;"
                    + " -fx-background-color: #EFF6FF; -fx-border-color: #BFDBFE;");
            panelStep.setVisible(false); panelStep.setManaged(false);
        } else {
            lblModeDesc.setText(DESC_SNIPE);
            lblModeDesc.setStyle(DESC_BASE + " -fx-text-fill: #92400e;"
                    + " -fx-background-color: #FFFBEB; -fx-border-color: #FDE68A;");
            panelStep.setVisible(true);  panelStep.setManaged(true);
        }

        refreshPreview();
    }

    // ─── Quick amount buttons ───────────────────────────────────────

    @FXML void handleQuickMax(ActionEvent event) {
        Button b = (Button) event.getSource();
        double add = Double.parseDouble(b.getUserData().toString());
        double cur = parseField(txtMaxBid);
        txtMaxBid.setText(String.valueOf((long) (cur + add)));
    }

    @FXML void handleQuickStep(ActionEvent event) {
        Button b = (Button) event.getSource();
        double add = Double.parseDouble(b.getUserData().toString());
        double cur = parseField(txtIncrement);
        txtIncrement.setText(String.valueOf((long) (cur + add)));
    }

    // ─── Preview ────────────────────────────────────────────────────

    private void refreshPreview() {
        double max  = parseField(txtMaxBid);
        double step = parseField(txtIncrement);
        if (step <= 0) step = 100_000;

        if (max <= currentPrice) {
            lblNextBid.setText("--");
            lblMaxTimes.setText("--");
            lblTotalMax.setText("--");
            return;
        }

        double nextBid = currentPrice + step;
        long   maxTimes = (long) Math.floor((max - currentPrice) / step);

        lblNextBid.setText(MoneyFormatter.formatViaPattern(nextBid));
        lblMaxTimes.setText(maxTimes + " lần");
        lblTotalMax.setText(MoneyFormatter.formatViaPattern(max));
    }

    // ─── Activate ───────────────────────────────────────────────────

    @FXML
    void handleActivate(ActionEvent event) {
        double max  = parseField(txtMaxBid);
        double step = parseField(txtIncrement);
        int    delay = (int) Math.round(sliderDelay.getValue());

        if (max <= 0) { Dialogs.warn("Lỗi", "Vui lòng nhập giá tối đa!"); return; }
        if (max <= currentPrice) {
            Dialogs.warn("Lỗi", "Giá tối đa phải cao hơn giá hiện tại ("
                    + MoneyFormatter.formatViaPattern(currentPrice) + ")!"); return;
        }
        if (max > UserSession.getInstance().getBalance()) {
            Dialogs.warn("Lỗi", "Giá tối đa vượt quá số dư ví ("
                    + MoneyFormatter.formatViaPattern(UserSession.getInstance().getBalance()) + ")!"); return;
        }

        String mode = "STEP";
        if (btnModeFixed.isSelected()) {
            mode = "FIXED";
            step = max; // giá cố định: increment = max bản thân
        } else if (btnModeSnipe.isSelected()) {
            mode = "SNIPE";
        }

        if ((mode.equals("STEP") || mode.equals("SNIPE")) && step < 100_000) {
            Dialogs.warn("Lỗi", "Bước giá tối thiểu là 100.000 đ!"); return;
        }

        AutoBidConfig cfg = new AutoBidConfig(max, step, delay, mode);
        if (onActivate != null) onActivate.accept(mode, cfg);
        closeStage();
    }

    @FXML void handleClose(ActionEvent event) { closeStage(); }

    // ─── Helpers ────────────────────────────────────────────────────

    private double parseField(TextField f) {
        try { return Double.parseDouble(f.getText().trim()); }
        catch (Exception e) { return 0; }
    }

    private void closeStage() {
        Stage stage = (Stage) btnActivate.getScene().getWindow();
        stage.close();
    }
}
