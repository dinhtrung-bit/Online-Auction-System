package client.controllers;

import client.ClientApp;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class AuctionListController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<AuctionViewModel> auctionTable;

    @FXML
    private TableColumn<AuctionViewModel, Integer> idColumn;

    @FXML
    private TableColumn<AuctionViewModel, String> itemNameColumn;

    @FXML
    private TableColumn<AuctionViewModel, Double> currentPriceColumn;

    @FXML
    private TableColumn<AuctionViewModel, String> winnerColumn;

    @FXML
    private TableColumn<AuctionViewModel, String> statusColumn;

    private final ObservableList<AuctionViewModel> auctionList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        currentPriceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        winnerColumn.setCellValueFactory(new PropertyValueFactory<>("currentWinner"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        currentPriceColumn.setCellFactory(column -> new TableCell<>() {
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

        statusColumn.setCellFactory(column -> new TableCell<>() {
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

        auctionList.add(new AuctionViewModel(1, "Laptop Dell XPS 13", 12000000, "userA", "RUNNING"));
        auctionList.add(new AuctionViewModel(2, "iPhone 13 Pro", 10000000, "userB", "RUNNING"));
        auctionList.add(new AuctionViewModel(3, "Tai nghe Sony WH-1000XM4", 2000000, "userC", "FINISHED"));

        auctionTable.setItems(auctionList);

        auctionTable.setRowFactory(tv -> {
            TableRow<AuctionViewModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetail(row.getItem());
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
        AuctionViewModel selected = auctionTable.getSelectionModel().getSelectedItem();

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

    private void openDetail(AuctionViewModel selected) {
        try {
            FXMLLoader loader = new FXMLLoader(
<<<<<<< HEAD
                    client.controllers.ClientApp.class.getResource("/views/auction-detail.fxml")
=======
                    ClientApp.class.getResource("/client/views/auction-detail.fxml")
>>>>>>> 975c8bcf6c07664b14552ffc5de027defdcb0a70
            );
            Scene scene = new Scene(loader.load(), 1000, 650);

            var cssUrl = ClientApp.class.getResource("/client/views/app.css");
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
                    ClientApp.class.getResource("/client/views/login.fxml")
            );
            Scene scene = new Scene(loader.load(), 900, 600);

            var cssUrl = ClientApp.class.getResource("/client/views/app.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            Stage stage = (Stage) auctionTable.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Login");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}