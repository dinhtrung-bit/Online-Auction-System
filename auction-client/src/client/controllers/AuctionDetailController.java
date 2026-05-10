package client.controllers;

import client.models.user.UserSession;
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
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
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
    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private javafx.scene.layout.VBox overlayFinished;
    @FXML private Label lblFinishIcon, lblFinishTitle, lblFinishMessage;

    private String currentRoomId;
    private String myUsername;
    private volatile int remainingSeconds = 0;
    private Timer timer;
    private final Gson gson = new Gson();

    private XYChart.Series<String, Number> priceSeries;
    private int bidCount = 0;

    private boolean isAutoBidActive = false;
    private double maxAutoBidAmount = 0.0;
    private double currentPriceVal = 0.0;
    private String lastWinner = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.myUsername = UserSession.username;

        setupModernInput(txtBidAmount);
        setupModernInput(txtAutoBidMax);

        stylePrimaryButton(btnPlaceBid);
        styleOutlineButton(btnToggleAutoBid);

        txtBidAmount.setPromptText("Nhập số tiền đấu giá...");
        txtAutoBidMax.setPromptText("Nhập giới hạn Auto-Bid...");

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
                if (btnPlaceBid != null) {
                    btnPlaceBid.setDisable(!canBid);
                }

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

                historyList.getItems().add(0,
                        lastWinner + " đặt " + formatPrice(data[1]) + " đ"
                );

                bidCount++;
                priceSeries.getData().add(
                        new XYChart.Data<>("Lần " + bidCount, currentPriceVal)
                );
            });
        });

        ClientMain.registerListener("AUTO_BID_EXCEEDED", payload -> {
            if (!payload.equals(currentRoomId)) return;

            Platform.runLater(() -> {
                isAutoBidActive = false;
                btnToggleAutoBid.setText("Kích hoạt lại");
                styleOutlineButton(btnToggleAutoBid);
                txtAutoBidMax.setDisable(false);
            });
        });

        ClientMain.registerListener("AUCTION_FINISHED", payload -> {
            if (!payload.equals(currentRoomId)) return;

            Platform.runLater(() -> {
                remainingSeconds = 0;
                lblTimer.setText("ĐÃ KẾT THÚC");

                if (btnPlaceBid != null) {
                    btnPlaceBid.setDisable(true);
                }

                if (timer != null) {
                    timer.cancel();
                }

                overlayFinished.setVisible(true);

                if (myUsername != null && myUsername.equals(lastWinner)) {
                    lblFinishIcon.setText("🏆");
                    lblFinishTitle.setText("CHÚC MỪNG CHIẾN THẮNG!");
                    lblFinishTitle.setStyle("-fx-text-fill: #10b981;");
                    lblFinishMessage.setText(
                            "Bạn đã đấu giá thành công với mức giá "
                                    + formatPrice(String.valueOf(currentPriceVal))
                                    + " đ"
                    );
                } else {
                    lblFinishIcon.setText("🛑");
                    lblFinishTitle.setText("PHIÊN ĐẤU GIÁ KẾT THÚC");
                    lblFinishTitle.setStyle("-fx-text-fill: #ef4444;");
                    lblFinishMessage.setText(
                            "Rất tiếc, sản phẩm đã thuộc về "
                                    + (lastWinner.isEmpty() ? "người khác" : lastWinner)
                    );
                }
            });
        });

        ClientMain.registerListener("BID_FAILED", payload ->
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.WARNING, "Đặt giá thất bại", payload)
                )
        );

        // Load lịch sử đấu giá từ DB — hiển thị đầy đủ dù client vào muộn hay thoát rồi vào lại
        ClientMain.registerListener("BID_HISTORY", payload -> {
            try {
                java.lang.reflect.Type listType =
                        new com.google.gson.reflect.TypeToken<java.util.List<java.util.Map<String, Object>>>() {}.getType();
                java.util.List<java.util.Map<String, Object>> bids = gson.fromJson(payload, listType);

                Platform.runLater(() -> {
                    historyList.getItems().clear();
                    priceSeries.getData().clear();
                    bidCount = 0;

                    if (bids == null || bids.isEmpty()) return;

                    for (java.util.Map<String, Object> bid : bids) {
                        String username = bid.get("username") != null ? bid.get("username").toString() : "?";
                        double amount   = bid.get("amount")   != null ? ((Number) bid.get("amount")).doubleValue() : 0;
                        String time     = bid.get("time")     != null ? bid.get("time").toString() : "";

                        // Cắt bỏ phần giây để hiển thị gọn
                        if (time.length() > 16) time = time.substring(0, 16);

                        historyList.getItems().add(0,
                                username + " đặt " + formatPrice(String.valueOf((long) amount)) + " đ  [" + time + "]"
                        );

                        bidCount++;
                        priceSeries.getData().add(new XYChart.Data<>("Lần " + bidCount, amount));

                        // Cập nhật người dẫn đầu theo bid mới nhất (list ASC, thêm vào đầu → cuối vòng = mới nhất)
                        lastWinner      = username;
                        currentPriceVal = amount;
                    }

                    lblWinner.setText("👤 Người dẫn đầu: " + lastWinner);
                });
            } catch (Exception e) {
                System.err.println("Lỗi parse BID_HISTORY: " + e.getMessage());
            }
        });
    }

    public void setRoomId(String id) {
        this.currentRoomId = id;

        // Lấy thông tin chi tiết phòng (giá, trạng thái, thời gian còn lại)
        ClientMain.send(gson.toJson(new MessageDTO("GET_AUCTION_DETAIL", id)));

        // Lấy toàn bộ lịch sử đấu giá từ DB — đảm bảo không mất dữ liệu khi thoát/vào lại
        ClientMain.send(gson.toJson(new MessageDTO("GET_BID_HISTORY", id)));
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
        String text = txtBidAmount.getText().trim();

        if (text.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu dữ liệu", "Vui lòng nhập số tiền đấu giá!");
            return;
        }

        double amount;

        try {
            amount = Double.parseDouble(text);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Số tiền không hợp lệ!");
            return;
        }

        if (amount <= currentPriceVal) {
            showAlert(Alert.AlertType.WARNING, "Không hợp lệ", "Giá đặt phải cao hơn giá hiện tại!");
            return;
        }

        if (amount > UserSession.balance) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Số dư không đủ",
                    "Bạn chỉ còn "
                            + formatPrice(String.valueOf(UserSession.balance))
                            + " đ trong ví. Vui lòng nạp thêm tiền để tiếp tục!"
            );
            return;
        }

        MessageDTO req = new MessageDTO(
                "BID",
                currentRoomId + ":" + myUsername + ":" + amount
        );

        ClientMain.send(gson.toJson(req));
        txtBidAmount.clear();
    }

    @FXML
    void handleToggleAutoBid(ActionEvent event) {
        if (!isAutoBidActive) {
            String rawMax = txtAutoBidMax.getText().trim();

            if (rawMax.isEmpty()) {
                showAlert(
                        Alert.AlertType.WARNING,
                        "Cảnh báo",
                        "Vui lòng nhập mức giá tối đa để kích hoạt Auto-Bid!"
                );
                return;
            }

            try {
                maxAutoBidAmount = Double.parseDouble(rawMax);
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Giới hạn Auto-Bid không hợp lệ!");
                return;
            }

            if (maxAutoBidAmount <= currentPriceVal) {
                showAlert(
                        Alert.AlertType.WARNING,
                        "Không hợp lệ",
                        "Giới hạn Auto-Bid phải cao hơn giá hiện tại!"
                );
                return;
            }

            if (maxAutoBidAmount > UserSession.balance) {
                showAlert(
                        Alert.AlertType.WARNING,
                        "Số dư không đủ",
                        "Giới hạn Auto-Bid không được lớn hơn số dư ví!"
                );
                return;
            }

            double increment = 100000.0;
            String payload = currentRoomId + ":" + maxAutoBidAmount + ":" + increment;

            MessageDTO req = new MessageDTO("SET_AUTO_BID", payload);
            ClientMain.send(gson.toJson(req));

            isAutoBidActive = true;
            btnToggleAutoBid.setText("⏹ Hủy Auto-Bid");
            txtAutoBidMax.setDisable(true);
            styleDangerButton(btnToggleAutoBid);

        } else {
            MessageDTO req = new MessageDTO("CANCEL_AUTO_BID", currentRoomId);
            ClientMain.send(gson.toJson(req));

            isAutoBidActive = false;
            btnToggleAutoBid.setText("Kích hoạt");
            txtAutoBidMax.setDisable(false);
            styleOutlineButton(btnToggleAutoBid);
        }
    }

    @FXML
    void handleBackToList(ActionEvent event) {
        if (timer != null) timer.cancel();

        ClientMain.unregisterListener("AUCTION_DETAIL_DATA");
        ClientMain.unregisterListener("UPDATE_PRICE");
        ClientMain.unregisterListener("AUCTION_FINISHED");
        ClientMain.unregisterListener("BID_FAILED");
        ClientMain.unregisterListener("AUTO_BID_EXCEEDED");
        ClientMain.unregisterListener("BID_HISTORY");

        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatPrice(String raw) {
        try {
            long val = (long) Double.parseDouble(raw);
            return String.format("%,d", val).replace(',', '.');
        } catch (Exception e) {
            return raw;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        DialogPane pane = alert.getDialogPane();
        pane.setStyle("""
                -fx-background-color: white;
                -fx-font-size: 14px;
                -fx-background-radius: 18;
                """);

        alert.showAndWait();
    }

    private void setupModernInput(TextField field) {
        applyInputNormalStyle(field);

        field.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (focused) {
                field.setStyle("""
                        -fx-background-color: white;
                        -fx-border-color: #3b82f6;
                        -fx-border-width: 2;
                        -fx-border-radius: 14;
                        -fx-background-radius: 14;
                        -fx-padding: 12 16;
                        -fx-font-size: 15px;
                        -fx-font-weight: bold;
                        -fx-text-fill: #0f172a;
                        -fx-effect: dropshadow(gaussian, rgba(59,130,246,0.25), 12, 0.2, 0, 2);
                        """);
            } else {
                applyInputNormalStyle(field);
            }
        });

        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                Platform.runLater(() ->
                        field.setText(newVal.replaceAll("[^\\d]", ""))
                );
            }
        });
    }

    private void applyInputNormalStyle(TextField field) {
        field.setStyle("""
                -fx-background-color: #f8fafc;
                -fx-border-color: #cbd5e1;
                -fx-border-radius: 14;
                -fx-background-radius: 14;
                -fx-padding: 12 16;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-text-fill: #0f172a;
                """);
    }

    private void stylePrimaryButton(Button button) {
        button.setStyle("""
                -fx-background-color: linear-gradient(to right, #10b981, #059669);
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-background-radius: 14;
                -fx-cursor: hand;
                -fx-padding: 14 20;
                -fx-effect: dropshadow(gaussian, rgba(16,185,129,0.28), 12, 0.2, 0, 4);
                """);

        button.setOnMouseEntered(e -> button.setStyle("""
                -fx-background-color: linear-gradient(to right, #059669, #047857);
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-background-radius: 14;
                -fx-cursor: hand;
                -fx-padding: 14 20;
                -fx-scale-x: 1.02;
                -fx-scale-y: 1.02;
                """));

        button.setOnMouseExited(e -> button.setStyle("""
                -fx-background-color: linear-gradient(to right, #10b981, #059669);
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-background-radius: 14;
                -fx-cursor: hand;
                -fx-padding: 14 20;
                -fx-effect: dropshadow(gaussian, rgba(16,185,129,0.28), 12, 0.2, 0, 4);
                """));
    }

    private void styleOutlineButton(Button button) {
        button.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #cbd5e1;
                -fx-border-width: 1.5;
                -fx-border-radius: 14;
                -fx-background-radius: 14;
                -fx-text-fill: #334155;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 12 18;
                -fx-cursor: hand;
                """);

        button.setOnMouseEntered(e -> button.setStyle("""
                -fx-background-color: #eff6ff;
                -fx-border-color: #3b82f6;
                -fx-border-width: 1.5;
                -fx-border-radius: 14;
                -fx-background-radius: 14;
                -fx-text-fill: #2563eb;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 12 18;
                -fx-cursor: hand;
                """));

        button.setOnMouseExited(e -> button.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #cbd5e1;
                -fx-border-width: 1.5;
                -fx-border-radius: 14;
                -fx-background-radius: 14;
                -fx-text-fill: #334155;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 12 18;
                -fx-cursor: hand;
                """));
    }

    private void styleDangerButton(Button button) {
        button.setStyle("""
                -fx-background-color: linear-gradient(to right, #ef4444, #dc2626);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 14;
                -fx-padding: 12 18;
                -fx-cursor: hand;
                """);

        button.setOnMouseEntered(e -> button.setStyle("""
                -fx-background-color: linear-gradient(to right, #dc2626, #b91c1c);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 14;
                -fx-padding: 12 18;
                -fx-cursor: hand;
                """));

        button.setOnMouseExited(e -> button.setStyle("""
                -fx-background-color: linear-gradient(to right, #ef4444, #dc2626);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 14;
                -fx-padding: 12 18;
                -fx-cursor: hand;
                """));
    }
}