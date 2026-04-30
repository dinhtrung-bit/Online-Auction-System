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

        // Dữ liệu người dùng mẫu
        ObservableList<UserViewModel> list = FXCollections.observableArrayList(
                new UserViewModel(101, "admin_toan", "Admin", "Hoạt động"),
                new UserViewModel(102, "seller_vip", "Seller", "Hoạt động"),
                new UserViewModel(103, "bidder_007", "Bidder", "Hoạt động"),
                new UserViewModel(104, "spammer_xyz", "Bidder", "Bị khóa")
        );

        tableUsers.setItems(list);
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1200, 700);

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}