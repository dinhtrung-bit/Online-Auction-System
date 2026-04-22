package client.controllers;

import client.ClientApp;
import client.models.AuctionViewModel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static java.io.FileDescriptor.in;

public class AuctionDetailController {

    @FXML
    private Label idLabel;

    @FXML
    private Label itemNameLabel;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private Label winnerLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField bidAmountField;

    @FXML
    private Button bidButton;

    private AuctionViewModel currentAuction;

    private BufferedReader in;
    private PrintWriter out;
    private Socket socket;

    public void setAuctionData(AuctionViewModel auction) {
        this.currentAuction = auction;
        idLabel.setText("ID: " + auction.getId());
        itemNameLabel.setText("Tên sản phẩm: " + auction.getItemName());
        currentPriceLabel.setText("Giá hiện tại: " + String.format("%,.0f VND", auction.getCurrentPrice()));
        winnerLabel.setText("Người dẫn đầu: " + auction.getCurrentWinner());
        statusLabel.setText("Trạng thái: " + auction.getStatus());

        if ("FINISHED".equalsIgnoreCase(auction.getStatus())) {
            bidAmountField.setDisable(true);
            bidButton.setDisable(true);
        }
    }

    @FXML
    public void handleBid(ActionEvent event) {
        String bidText = bidAmountField.getText();

        if (bidText == null || bidText.isBlank()) {
            showAlert("Lỗi", "Vui lòng nhập giá đấu");
            return;
        }

        try {
            double bidAmount = Double.parseDouble(bidText);

            if (currentAuction == null) {
                showAlert("Lỗi", "Không có dữ liệu phiên đấu giá");
                return;
            }

            if (bidAmount <= currentAuction.getCurrentPrice()) {
                showAlert("Lỗi", "Giá đặt phải lớn hơn giá hiện tại");
                return;
            }

            currentPriceLabel.setText("Giá hiện tại: " + String.format("%,.0f VND", bidAmount));
            showAlert("Thành công", "Đặt giá thành công: " + String.format("%,.0f VND", bidAmount));

        } catch (NumberFormatException e) {
            showAlert("Lỗi", "Giá đấu phải là số");
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    ClientApp.class.getResource("/client/views/auction-list.fxml")
            );
            Scene scene = new Scene(loader.load(), 1000, 650);

            var cssUrl = ClientApp.class.getResource("/client/views/app.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            Stage stage = (Stage) bidAmountField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Auction List");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void initialize() throws IOException {
        socket = new Socket("localhost", 8080);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Thread listenerThread = new Thread(() -> {
            try {
                while (true) {
                    String serverMessage = in.readLine(); // Radar liên tục nghe Server
                    if (serverMessage == null) break;

                    // NẾU RADAR BẮT ĐƯỢC TÍN HIỆU TỪ LOA PHƯỜNG:
                    if (serverMessage.startsWith("NEW_HIGH_BID:")) {
                        // Bóc tách lấy con số (Cắt bỏ chữ "NEW_HIGH_BID:")
                        String newPrice = serverMessage.split(":")[1];

                        // NHỜ LUỒNG CHÍNH CẬP NHẬT GIAO DIỆN
                        Platform.runLater(() -> {
                            // Giả sử nhãn hiển thị giá của bạn tên là CurrentPrice
                            currentPriceLabel.setText(newPrice + " VNĐ");
                            System.out.println("Giao dien da cap nhat gia moi: " + newPrice);
                        });
                    }
                    // (Bạn có thể thêm các if khác ở đây để xử lý các tin nhắn khác)
                }
            } catch (Exception e) {
                System.out.println("Mất kết nối ...");
                e.printStackTrace()  ;
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}