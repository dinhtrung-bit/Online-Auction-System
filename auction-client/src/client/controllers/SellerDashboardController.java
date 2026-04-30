package client.controllers;

import client.models.AuctionViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class SellerDashboardController implements Initializable {

    @FXML private TableView<AuctionViewModel> tableItems;
    @FXML private TableColumn<AuctionViewModel, Integer> colId;
    @FXML private TableColumn<AuctionViewModel, String> colName;
    @FXML private TableColumn<AuctionViewModel, Double> colPrice;
    @FXML private TableColumn<AuctionViewModel, String> colWinner;
    @FXML private TableColumn<AuctionViewModel, String> colStatus;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Gắn cột với các thuộc tính trong AuctionViewModel
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colWinner.setCellValueFactory(new PropertyValueFactory<>("currentWinner"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Tạo dữ liệu giả lập (Demo)
        ObservableList<AuctionViewModel> list = FXCollections.observableArrayList(
                new AuctionViewModel(1, "Samsung QLED 4K 55\"", 18200000, "bidder1", "Đang diễn ra"),
                new AuctionViewModel(2, "Honda City RS 2024", 480000000, "bidder3", "Kết thúc"),
                new AuctionViewModel(3, "Macbook Pro M3 Max", 85000000, "Chưa có", "Sắp mở")
        );

        tableItems.setItems(list);
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1200, 700);

            // Tắt Fullscreen khi quay lại màn Login
            stage.setMaximized(false);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}