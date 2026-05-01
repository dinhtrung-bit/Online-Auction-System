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
import com.google.gson.Gson;

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

        // Gửi yêu cầu lấy danh sách Phiên đấu giá lên Server
        client.networks.MessageDTO req = new client.networks.MessageDTO("GET_ALL_AUCTIONS", "");
        client.networks.ClientMain.send(new Gson().toJson(req));

        try {
            // Chờ nhận danh sách từ Server
            String resJson = client.networks.ClientMain.receive();
            if (resJson != null) {
                client.networks.MessageDTO res = new Gson().fromJson(resJson, client.networks.MessageDTO.class);

                // Giả sử Server trả về Action tên là "AUCTION_LIST"
                if ("AUCTION_LIST".equals(res.getAction())) {

                    // Ép kiểu chuỗi JSON thành List<AuctionViewModel>
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.ArrayList<AuctionViewModel>>(){}.getType();
                    java.util.List<AuctionViewModel> serverList = new Gson().fromJson(res.getPayload(), listType);

                    // Đưa dữ liệu thật vào bảng
                    ObservableList<AuctionViewModel> list = FXCollections.observableArrayList(serverList);
                    tableItems.setItems(list);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Giữ nguyên khung màn hình to, chỉ thay ruột về trang Login
            stage.getScene().setRoot(root);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}