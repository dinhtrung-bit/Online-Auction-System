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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class AuctionDetailController implements Initializable {

    @FXML private ListView<String> historyList;
    @FXML private TextField txtBidAmount;
    @FXML private Label lblTimer;
    @FXML private Label lblCurrentPrice;
    private volatile boolean isRunning = true;

    private int seconds = 3560;
    private Timer timer;

    private Gson gson = new Gson();

    //cần sửa 2 biến này để đăng nhập thật
    private Long currentRoomId = 1L;
    private String myUsername = "nguoidung1";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Đảm bảo mạng đã được kết nối
        ClientMain.connectToServer();

        startTimer();


        startListeningFromServer();
    }

    private void startTimer() {
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (seconds > 0 && lblTimer != null) {
                        seconds--;
                        lblTimer.setText(String.format("%02d:%02d:%02d", seconds/3600, (seconds%3600)/60, seconds%60));
                    }
                });
            }
        }, 0, 1000);
    }




    private void startListeningFromServer() {
        Thread listenerThread = new Thread(() -> {
            try {
                while (isRunning) {
                    String json = ClientMain.receive();
                    if (json == null) continue;

                    MessageDTO msg = gson.fromJson(json, MessageDTO.class);


                    if ("UPDATE_PRICE".equals(msg.getAction())) {
                        String[] data = msg.getPayload().split(":");
                        Long roomId = Long.parseLong(data[0]);
                        String newPrice = data[1];
                        String bidderName = data[2];


                        if (roomId.equals(currentRoomId)) {
                            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            String displayTxt = time + " - " + bidderName + " vừa đặt: " + newPrice + " đ";


                            Platform.runLater(() -> {
                                if (lblCurrentPrice != null) lblCurrentPrice.setText(newPrice + " đ");
                                historyList.getItems().add(0, displayTxt);
                            });
                        }
                    }

                    else if ("BID_FAILED".equals(msg.getAction())) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR, msg.getPayload());
                            alert.show();
                        });
                    }
                }
            } catch (Exception e) {
                System.out.println("Đã ngắt kết nối lắng nghe từ Server.");
            }
        });

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @FXML
    void handlePlaceBid() {
        String amount = txtBidAmount.getText().trim();
        if (!amount.isEmpty()) {


            MessageDTO req = new MessageDTO();
            req.setAction("BID");
            req.setPayload(currentRoomId + ":" + myUsername + ":" + amount);

            // Gửi lên Server
            ClientMain.send(gson.toJson(req));

            txtBidAmount.clear();
        }
    }


    @FXML
    void handleBackToList(ActionEvent event) {
        if (timer != null) timer.cancel();
        isRunning = false;

        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();


            Scene scene = stage.getScene();
            scene.setRoot(root);


            if (root instanceof javafx.scene.layout.Region) {
                javafx.scene.layout.Region region = (javafx.scene.layout.Region) root;
                region.prefWidthProperty().bind(scene.widthProperty());
                region.prefHeightProperty().bind(scene.heightProperty());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}