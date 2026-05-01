package client.controllers;

import client.models.UserViewModel;
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

public class AdminDashboardController implements Initializable {

    @FXML private TableView<UserViewModel> tableUsers;
    @FXML private TableColumn<UserViewModel, Integer> colId;
    @FXML private TableColumn<UserViewModel, String> colUsername;
    @FXML private TableColumn<UserViewModel, String> colRole;
    @FXML private TableColumn<UserViewModel, String> colStatus;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Gắn các cột với thuộc tính trong UserViewModel
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Gửi yêu cầu lấy danh sách User lên Server
        client.networks.MessageDTO req = new client.networks.MessageDTO("GET_ALL_USERS", "");
        client.networks.ClientMain.send(new Gson().toJson(req));

        try {
            // Chờ nhận danh sách từ Server
            String resJson = client.networks.ClientMain.receive();
            if (resJson != null) {
                client.networks.MessageDTO res = new Gson().fromJson(resJson, client.networks.MessageDTO.class);

                if ("USER_LIST".equals(res.getAction())) {
                    // Ép kiểu chuỗi JSON thành List<UserViewModel>
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.ArrayList<UserViewModel>>(){}.getType();
                    java.util.List<UserViewModel> serverList = new Gson().fromJson(res.getPayload(), listType);

                    // Đưa dữ liệu thật vào bảng
                    ObservableList<UserViewModel> list = FXCollections.observableArrayList(serverList);
                    tableUsers.setItems(list);
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