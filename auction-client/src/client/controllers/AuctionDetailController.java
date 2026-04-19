package client.controllers;

import client.ClientApp;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

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
}