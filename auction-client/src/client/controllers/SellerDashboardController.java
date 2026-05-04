package client.controllers;

import client.models.AuctionViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colWinner.setCellValueFactory(new PropertyValueFactory<>("currentWinner"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        client.networks.ClientMain.registerListener("AUCTION_LIST", payload -> {
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.ArrayList<AuctionViewModel>>(){}.getType();
            java.util.List<AuctionViewModel> serverList = new Gson().fromJson(payload, listType);
            Platform.runLater(() -> {
                tableItems.setItems(FXCollections.observableArrayList(serverList));
            });
        });

        refreshTable();
    }

    private void refreshTable() {
        client.networks.MessageDTO req = new client.networks.MessageDTO("GET_ALL_AUCTIONS", "");
        client.networks.ClientMain.send(new Gson().toJson(req));
    }

    @FXML
    void showAddProductDialog(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Đăng Bán Sản Phẩm Mới");
        dialog.setHeaderText("Nhập thông tin phiên đấu giá");

        TextField txtName = new TextField(); txtName.setPromptText("Tên sản phẩm");
        TextField txtPrice = new TextField(); txtPrice.setPromptText("Giá khởi điểm (VNĐ)");
        TextField txtDuration = new TextField(); txtDuration.setPromptText("Thời gian (phút)");

        VBox vbox = new VBox(10, new Label("Tên sản phẩm:"), txtName, new Label("Giá khởi điểm:"), txtPrice, new Label("Thời gian (phút):"), txtDuration);
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    JsonObject payloadObj = new JsonObject();
                    payloadObj.addProperty("name", txtName.getText());
                    payloadObj.addProperty("startingPrice", Double.parseDouble(txtPrice.getText()));
                    payloadObj.addProperty("durationMinutes", Integer.parseInt(txtDuration.getText()));

                    client.networks.MessageDTO req = new client.networks.MessageDTO("CREATE_AUCTION", payloadObj.toString());
                    client.networks.ClientMain.send(new Gson().toJson(req));

                    showAlert("Thành công", "Đã gửi yêu cầu tạo đấu giá!");
                    refreshTable();
                } catch (Exception e) {
                    showAlert("Lỗi", "Vui lòng nhập dữ liệu hợp lệ!");
                }
            }
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }
}