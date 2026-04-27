package client.controllers;

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

    private int seconds = 3560;
    private Timer timer;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        startTimer();
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

    @FXML
    void handlePlaceBid() {
        String amount = txtBidAmount.getText().trim();
        if (!amount.isEmpty()) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            historyList.getItems().add(0, time + " - Bạn: " + amount + " đ");
            if (lblCurrentPrice != null) lblCurrentPrice.setText(amount + " đ");
            txtBidAmount.clear();
        }
    }

    @FXML
    void handleBackToList(ActionEvent event) {
        if (timer != null) timer.cancel();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception e) { e.printStackTrace(); }
    }
}