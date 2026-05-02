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
    @FXML private Button btnPlaceBid; // Cần thêm @FXML này và gán trong Scene Builder

    private String currentRoomId;
    private String myUsername;
    private volatile boolean isRunning = true;
    private int remainingSeconds = 0;
    private Timer timer;
    private Gson gson = new Gson();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.myUsername = client.models.UserSession.username;
        ClientMain.connectToServer();
        startListeningFromServer();
    }

    // Hàm nhận ID từ AuctionList và yêu cầu dữ liệu thật từ Server
    public void setRoomId(String id) {
        this.currentRoomId = id;
        // Gửi yêu cầu lấy thông tin chi tiết và thời gian còn lại từ DB
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
                        btnPlaceBid.setDisable(true); // Tự động khóa nút khi hết giờ
                    }
                });
            }
        }, 0, 1000);
    }

    private void startListeningFromServer() {
        new Thread(() -> {
            try {
                while (isRunning) {
                    String json = ClientMain.receive();
                    if (json == null) continue;
                    MessageDTO msg = gson.fromJson(json, MessageDTO.class);

                    // Nhận dữ liệu khởi tạo phòng (Thời gian, Giá hiện tại)
                    if ("AUCTION_DETAIL_DATA".equals(msg.getAction())) {
                        // Giả sử payload: "giá:thời_gian_giây:trạng_thái"
                        String[] data = msg.getPayload().split(":");
                        Platform.runLater(() -> {
                            lblCurrentPrice.setText(data[0] + " đ");
                            this.remainingSeconds = Integer.parseInt(data[1]);
                            boolean isOpen = "OPENING".equals(data[2]);
                            btnPlaceBid.setDisable(!isOpen); // Seller chưa mở thì Bidder không ấn được
                            startTimer();
                        });
                    }

                    // Cập nhật giá Real-time khi người khác đặt
                    else if ("UPDATE_PRICE".equals(msg.getAction())) {
                        String[] data = msg.getPayload().split(":");
                        if (data[0].equals(currentRoomId)) {
                            Platform.runLater(() -> {
                                lblCurrentPrice.setText(data[1] + " đ");
                                historyList.getItems().add(0, data[2] + " đã đặt " + data[1] + " đ");
                            });
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML
    void handlePlaceBid() {
        String amount = txtBidAmount.getText().trim();
        if (!amount.isEmpty() && currentRoomId != null) {
            MessageDTO req = new MessageDTO("BID", currentRoomId + ":" + myUsername + ":" + amount);
            ClientMain.send(gson.toJson(req));
            txtBidAmount.clear();
        }
    }

    @FXML
    void handleBackToList(ActionEvent event) {
        isRunning = false;
        if (timer != null) timer.cancel();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }
}