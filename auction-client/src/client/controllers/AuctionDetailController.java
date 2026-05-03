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
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class AuctionDetailController implements Initializable {

    @FXML private ListView<String> historyList;
    @FXML private TextField txtBidAmount;
    @FXML private Label lblTimer;
    @FXML private Label lblCurrentPrice;
    @FXML private Button btnPlaceBid;

    private String currentRoomId;
    private String myUsername;
    private volatile int remainingSeconds = 0;
    private Timer timer;
    private final Gson gson = new Gson();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.myUsername = client.models.UserSession.username;

        // Đăng ký listener cho dữ liệu khởi tạo phòng
        ClientMain.registerListener("AUCTION_DETAIL_DATA", payload -> {
            // payload: "price:secondsLeft:status"
            String[] data = payload.split(":");
            if (data.length < 3) return;
            Platform.runLater(() -> {
                lblCurrentPrice.setText(formatPrice(data[0]) + " đ");
                remainingSeconds = Integer.parseInt(data[1]);
                boolean canBid = "RUNNING".equalsIgnoreCase(data[2]);
                if (btnPlaceBid != null) btnPlaceBid.setDisable(!canBid);
                startTimer();
            });
        });

        // Đăng ký listener realtime khi có bid mới từ bất kỳ client nào
        ClientMain.registerListener("UPDATE_PRICE", payload -> {
            // payload: "roomId:price:username"
            String[] data = payload.split(":");
            if (data.length < 3) return;
            if (!data[0].equals(currentRoomId)) return; // Không phải phòng này
            Platform.runLater(() -> {
                lblCurrentPrice.setText(formatPrice(data[1]) + " đ");
                historyList.getItems().add(0, data[2] + " đặt " + formatPrice(data[1]) + " đ");
            });
        });

        // Đăng ký listener khi Server thông báo phiên kết thúc
        ClientMain.registerListener("AUCTION_FINISHED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            Platform.runLater(() -> {
                remainingSeconds = 0;
                lblTimer.setText("ĐÃ KẾT THÚC");
                if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
                historyList.getItems().add(0, "⏹ Phiên đấu giá đã kết thúc");
                if (timer != null) timer.cancel();
            });
        });

        // Đăng ký listener khi Server gia hạn thời gian (anti-sniping)
        ClientMain.registerListener("UPDATE_TIMER", payload -> {
            // payload: "roomId:newSecondsLeft"
            String[] data = payload.split(":");
            if (data.length < 2 || !data[0].equals(currentRoomId)) return;
            Platform.runLater(() -> {
                remainingSeconds = Integer.parseInt(data[1]);
                historyList.getItems().add(0, "⏱ Phiên được gia hạn thêm!");
            });
        });
    }

    /** Được gọi từ AuctionListController sau khi load FXML xong */
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
                        lblTimer.setText("ĐÃ KẾT THÚC");
                        if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
                        timer.cancel();
                    }
                });
            }
        }, 1000, 1000);
    }

    @FXML
    void handlePlaceBid() {
        String amount = txtBidAmount.getText().trim();
        if (!amount.isEmpty() && currentRoomId != null) {
            MessageDTO req = new MessageDTO("BID",
                    currentRoomId + ":" + myUsername + ":" + amount);
            ClientMain.send(gson.toJson(req));
            txtBidAmount.clear();
        }
    }

    @FXML
    void handleBackToList(ActionEvent event) {
        // Dọn dẹp: huỷ timer + unregister các listener của màn hình này
        if (timer != null) timer.cancel();
        ClientMain.unregisterListener("AUCTION_DETAIL_DATA");
        ClientMain.unregisterListener("UPDATE_PRICE");
        ClientMain.unregisterListener("AUCTION_FINISHED");
        ClientMain.unregisterListener("UPDATE_TIMER");

        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/client/views/auction-list.fxml"));
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
}