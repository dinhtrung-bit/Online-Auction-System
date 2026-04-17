<<<<<<< HEAD:auction-client/src/main/java/com/auctions/client/controllers/AuctionListController.java
package com.auctions.client.controllers;
=======
package client.controllers;
>>>>>>> main:auction-client/src/client/controllers/AuctionListController.java

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
        currentPriceColumn.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f VND", price));
                }
            }
        });
        winnerColumn.setCellValueFactory(new PropertyValueFactory<>("currentWinner"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);

                    if ("RUNNING".equalsIgnoreCase(status)) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else if ("FINISHED".equalsIgnoreCase(status)) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        auctionList.add(new Auction(1, "Laptop Dell", 12000000, "userA", "RUNNING"));
        auctionList.add(new Auction(2, "iPhone 13", 10000000, "userB", "RUNNING"));
        auctionList.add(new Auction(3, "Tai nghe Sony", 2000000, "userC", "FINISHED"));

        auctionTable.setItems(auctionList);
        auctionTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Auction> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Auction selected = row.getItem();
                    openDetail(selected);
                }
            });
            return row;
        });
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

        openDetail(selected);
    }
    private void openDetail(Auction selected) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    ClientApp.class.getResource("/views/auction-detail.fxml")
            );
            Scene scene = new Scene(loader.load(), 1000, 650);

            var cssUrl = ClientApp.class.getResource("/styles/app.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

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