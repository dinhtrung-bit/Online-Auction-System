<<<<<<< HEAD
<<<<<<< HEAD:auction-client/src/main/java/com/auctions/client/controllers/AuctionDetailController.java
package com.auctions.client.controllers;
=======
package client.controllers;
>>>>>>> main:auction-client/src/client/controllers/AuctionDetailController.java
=======
package src.client.controllers;
>>>>>>> e6fdc0c29820aeb4b000a79a18715e91b6bec51e

import com.auctions.client.ClientApp;
import com.auctions.client.models.Auction;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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

    private Auction currentAuction;

    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        idLabel.setText("ID: " + auction.getId());
        itemNameLabel.setText("Tên sản phẩm: " + auction.getItemName());
        currentPriceLabel.setText("Giá hiện tại: " + auction.getCurrentPrice());
        winnerLabel.setText("Người dẫn đầu: " + auction.getCurrentWinner());
        statusLabel.setText("Trạng thái: " + auction.getStatus());
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

            showAlert("Thành công", "Đặt giá thành công: " + bidAmount);

        } catch (NumberFormatException e) {
            showAlert("Lỗi", "Giá đấu phải là số");
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    ClientApp.class.getResource("/views/auction-list.fxml")
            );
            Scene scene = new Scene(loader.load(), 1000, 650);
            scene.getStylesheets().add(
                    ClientApp.class.getResource("/src/client/styles/app.css").toExternalForm()
            );

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