package client.controllers;

import client.networks.NetworkManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.io.IOException;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Lỗi", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        // Gửi yêu cầu đăng nhập qua Socket (chạy trên thread riêng của NetworkManager)
        // Cấu trúc payload: username|password
        NetworkManager.getInstance().sendRequest("LOGIN", username + "|" + password, response -> {
            // Quay lại luồng UI để cập nhật giao diện
            Platform.runLater(() -> {
                if ("LOGIN_SUCCESS".equals(response.getAction())) {
                    navigateToAuctionList(event);
                } else {
                    showAlert("Thất bại", "Sai tài khoản hoặc mật khẩu!");
                }
            });
        });
    }

    private void navigateToAuctionList(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Danh sách sản phẩm đấu giá");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi", "Không thể tải màn hình danh sách sản phẩm.");
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    @FXML
    public void handleRegister(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText("Tính năng đăng ký đang được phát triển!");
        alert.showAndWait();
    }
}