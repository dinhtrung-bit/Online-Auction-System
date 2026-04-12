package com.auctions.client.controllers;

import com.auctions.client.ClientApp;
import com.auctions.client.models.Auction;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class AuctionListController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Auction> auctionTable;

    @FXML
    private TableColumn<Auction, Integer> idColumn;

    @FXML
    private TableColumn<Auction, String> itemNameColumn;

    @FXML
    private TableColumn<Auction, Double> currentPriceColumn;

    @FXML
    private TableColumn<Auction, String> winnerColumn;

    @FXML
    private TableColumn<Auction, String> statusColumn;

    private final ObservableList<Auction> auctionList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        currentPriceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        winnerColumn.setCellValueFactory(new PropertyValueFactory<>("currentWinner"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        auctionList.add(new Auction(1, "Laptop Dell", 12000000, "userA", "RUNNING"));
        auctionList.add(new Auction(2, "iPhone 13", 10000000, "userB", "RUNNING"));
        auctionList.add(new Auction(3, "Tai nghe Sony", 2000000, "userC", "FINISHED"));

        auctionTable.setItems(auctionList);
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        auctionTable.refresh();
    }

    @FXML
    public void handleViewDetail(ActionEvent event) {
        Auction selected = auctionTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cảnh báo");
            alert.setHeaderText(null);
            alert.setContentText("Vui lòng chọn một phiên đấu giá");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    ClientApp.class.getResource("/views/auction-detail.fxml")
            );
            Scene scene = new Scene(loader.load(), 1000, 650);
            scene.getStylesheets().add(
                    ClientApp.class.getResource("/styles/app.css").toExternalForm()
            );

            AuctionDetailController controller = loader.getController();
            controller.setAuctionData(selected);

            Stage stage = (Stage) auctionTable.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Auction Detail");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    ClientApp.class.getResource("/views/login.fxml")
            );
            Scene scene = new Scene(loader.load(), 900, 600);
            scene.getStylesheets().add(
                    ClientApp.class.getResource("/styles/app.css").toExternalForm()
            );

            Stage stage = (Stage) auctionTable.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Login");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}