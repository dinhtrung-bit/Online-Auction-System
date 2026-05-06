package client.controllers;

import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
// Import thêm Chart
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class AuctionDetailController implements Initializable {

    @FXML private ListView<String> historyList;
    @FXML private TextField txtBidAmount, txtAutoBidMax;
    @FXML private Label lblTimer, lblCurrentPrice, lblWinner;
    @FXML private Button btnPlaceBid, btnToggleAutoBid;

    // Thêm khai báo Chart
    @FXML private LineChart<String, Number> bidHistoryChart;

    @FXML private VBox overlayFinished;
    @FXML private Label lblFinishIcon, lblFinishTitle, lblFinishMessage;

    private String currentRoomId;
    private String myUsername;
    private volatile int remainingSeconds = 0;
    private Timer timer;
    private final Gson gson = new Gson();

    // Logic quản lý Chart
    private XYChart.Series<String, Number> priceSeries;
    private int bidCount = 0;

    // Auto-bid logic
    private boolean isAutoBidActive = false;
    private double maxAutoBidAmount = 0.0;
    private double currentPriceVal = 0.0;
    private String lastWinner = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.myUsername = client.models.UserSession.username;

        // Khởi tạo series cho biểu đồ
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Diễn biến giá");
        if (bidHistoryChart != null) {
            bidHistoryChart.getData().add(priceSeries);
        }

        ClientMain.registerListener("AUCTION_DETAIL_DATA", payload -> {
            String[] data = payload.split(":");
            if (data.length < 3) return;
            Platform.runLater(() -> {
                currentPriceVal = Double.parseDouble(data[0]);
                lblCurrentPrice.setText(formatPrice(data[0]) + " đ");
                remainingSeconds = Integer.parseInt(data[1]);
                boolean canBid = "RUNNING".equalsIgnoreCase(data[2]);
                if (btnPlaceBid != null) btnPlaceBid.setDisable(!canBid);

                // Reset biểu đồ khi vào phòng
                priceSeries.getData().clear();
                bidCount = 0;
                priceSeries.getData().add(new XYChart.Data<>("Bắt đầu", currentPriceVal));

                startTimer();
            });
        });

        ClientMain.registerListener("UPDATE_PRICE", payload -> {
            String[] data = payload.split(":");
            if (data.length < 3 || !data[0].equals(currentRoomId)) return;
            Platform.runLater(() -> {
                currentPriceVal = Double.parseDouble(data[1]);
                lastWinner = data[2];
                lblCurrentPrice.setText(formatPrice(data[1]) + " đ");
                lblWinner.setText("👤 Người dẫn đầu: " + lastWinner);
                historyList.getItems().add(0, lastWinner + " đặt " + formatPrice(data[1]) + " đ");

                // Cập nhật biểu đồ real-time
                bidCount++;
                priceSeries.getData().add(new XYChart.Data<>("Lần " + bidCount, currentPriceVal));

                // ĐÃ XÓA LOGIC AUTO-BID CỦA CLIENT Ở ĐÂY
                // Do mọi thao tác giờ do Server xử lý qua thuật toán PriorityQueue
            });
        });

        // Thêm listener xử lý khi Server báo Auto-Bid đã vượt quá giới hạn hoặc thất bại
        ClientMain.registerListener("AUTO_BID_EXCEEDED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            Platform.runLater(() -> {
                isAutoBidActive = false;
                btnToggleAutoBid.setText("Kích hoạt lại (Đã vượt mức)");
                btnToggleAutoBid.setStyle("-fx-text-fill: #ef4444; -fx-border-color: #ef4444;");
                txtAutoBidMax.setDisable(false);
            });
        });

        ClientMain.registerListener("AUCTION_FINISHED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            Platform.runLater(() -> {
                remainingSeconds = 0;
                lblTimer.setText("ĐÃ KẾT THÚC");
                if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
                if (timer != null) timer.cancel();

                overlayFinished.setVisible(true);
                if (myUsername.equals(lastWinner)) {
                    lblFinishIcon.setText("🏆");
                    lblFinishTitle.setText("CHÚC MỪNG CHIẾN THẮNG!");
                    lblFinishTitle.setStyle("-fx-text-fill: #10b981;");
                    lblFinishMessage.setText("Bạn đã đấu giá thành công với mức giá " + formatPrice(String.valueOf(currentPriceVal)) + " đ");
                } else {
                    lblFinishIcon.setText("🛑");
                    lblFinishTitle.setText("PHIÊN ĐẤU GIÁ KẾT THÚC");
                    lblFinishTitle.setStyle("-fx-text-fill: #ef4444;");
                    lblFinishMessage.setText("Rất tiếc, sản phẩm đã thuộc về " + (lastWinner.isEmpty() ? "người khác" : lastWinner));
                }
            });
        });

        ClientMain.registerListener("BID_FAILED", payload -> Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Đặt giá thất bại", payload)));
    }

    public void setRoomId(String id) {
        this.currentRoomId = id;
        MessageDTO req = new MessageDTO("GET_AUCTION_DETAIL", id);
        ClientMain.send(gson.toJson(req));
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (remainingSeconds > 0) {
                        remainingSeconds--;
                        int h = remainingSeconds / 3600;
                        int m = (remainingSeconds % 3600) / 60;
                        int s = remainingSeconds % 60;
                        lblTimer.setText(String.format("%02d:%02d:%02d", h, m, s));
                    } else {
                        lblTimer.setText("ĐANG CHỐT...");
                        timer.cancel();
                    }
                });
            }
        }, 1000, 1000);
    }

    @FXML
    void handlePlaceBid() {
        String rawAmount = txtBidAmount.getText().replaceAll("[^\\d]", "");
        if (!rawAmount.isEmpty() && currentRoomId != null) {
            double amount = Double.parseDouble(rawAmount);
            if (amount <= currentPriceVal) {
                showAlert(Alert.AlertType.WARNING, "Không hợp lệ", "Giá đặt phải cao hơn giá hiện tại!");
                return;
            }
            if (amount > client.models.UserSession.balance) {
                showAlert(Alert.AlertType.WARNING, "Số dư không đủ",
                        "Bạn chỉ còn " + formatPrice(String.valueOf(client.models.UserSession.balance)) +
                                " đ trong ví. Vui lòng nạp thêm tiền để tiếp tục!");
                return;
            }
            MessageDTO req = new MessageDTO("BID", currentRoomId + ":" + myUsername + ":" + amount);
            ClientMain.send(gson.toJson(req));
            txtBidAmount.clear();
        }
    }

    @FXML
    void handleToggleAutoBid(ActionEvent event) {
        if (!isAutoBidActive) {
            String rawMax = txtAutoBidMax.getText().replaceAll("[^\\d]", "");
            if (rawMax.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập mức giá tối đa để kích hoạt Auto-Bid!");
                return;
            }

            maxAutoBidAmount = Double.parseDouble(rawMax);
            double increment = 100000.0; // Bước giá mặc định là 100.000đ

            // ĐÓNG GÓI PAYLOAD GỬI LÊN SERVER (roomId:maxBid:increment)
            String payload = currentRoomId + ":" + maxAutoBidAmount + ":" + increment;
            MessageDTO req = new MessageDTO("SET_AUTO_BID", payload);
            ClientMain.send(gson.toJson(req));

            isAutoBidActive = true;
            btnToggleAutoBid.setText("⏹ Hủy Auto-Bid (Đang bật)");
            btnToggleAutoBid.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;");
            txtAutoBidMax.setDisable(true);
        } else {
            // NẾU NGƯỜI DÙNG CHỦ ĐỘNG HỦY AUTO-BID
            MessageDTO req = new MessageDTO("CANCEL_AUTO_BID", currentRoomId);
            ClientMain.send(gson.toJson(req));

            isAutoBidActive = false;
            btnToggleAutoBid.setText("Kích hoạt Auto-Bid");
            btnToggleAutoBid.setStyle("");
            txtAutoBidMax.setDisable(false);
        }
    }

    @FXML
    void handleBackToList(ActionEvent event) {
        if (timer != null) timer.cancel();
        // Clear listeners khi rời khỏi phòng
        ClientMain.unregisterListener("AUCTION_DETAIL_DATA");
        ClientMain.unregisterListener("UPDATE_PRICE");
        ClientMain.unregisterListener("AUCTION_FINISHED");
        ClientMain.unregisterListener("BID_FAILED");
        ClientMain.unregisterListener("AUTO_BID_EXCEEDED");
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String formatPrice(String raw) {
        try {
            long val = (long) Double.parseDouble(raw);
            return String.format("%,d", val).replace(',', '.');
        } catch (Exception e) { return raw; }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content); alert.show();
    }
}