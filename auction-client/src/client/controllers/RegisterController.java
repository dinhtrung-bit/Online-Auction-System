package client.controllers;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class RegisterController {

    @FXML private Button btnSeller, btnBidder;
    @FXML private TextField txtFullName, txtUsername;
    @FXML private PasswordField txtPassword;

    // Mặc định là Bidder (Người mua)
    private String selectedRole = "Bidder";
    private Gson gson = new Gson();

    private final String IDLE_STYLE = "-fx-background-color: white; -fx-border-color: #D1D5DB; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-text-fill: #374151;";
    private final String ACTIVE_STYLE = "-fx-background-color: #ECFDF5; -fx-text-fill: #10B981; -fx-border-color: #34D399; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-cursor: hand;";

    @FXML
    public void initialize() {
        // Thiết lập trạng thái ban đầu cho nút bấm
        updateButtonStyles();
    }

    @FXML
    void selectSeller(ActionEvent event) {
        selectedRole = "Seller";
        updateButtonStyles();
    }

    @FXML
    void selectBidder(ActionEvent event) {
        selectedRole = "Bidder";
        updateButtonStyles();
    }

    private void updateButtonStyles() {
        btnSeller.setStyle(selectedRole.equals("Seller") ? ACTIVE_STYLE : IDLE_STYLE);
        btnBidder.setStyle(selectedRole.equals("Bidder") ? ACTIVE_STYLE : IDLE_STYLE);
    }

    @FXML
    void goToLogin(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleRegister(ActionEvent event) {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        // 1. Kiểm tra nhập liệu
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đủ thông tin!");
            return;
        }

        Node registerBtn = (Node) event.getSource();
        registerBtn.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                // 2. Kết nối tới Server
                client.networks.ClientMain.connectToServer();

                // 3. ĐÓNG GÓI PAYLOAD: Quan trọng nhất là thứ tự các trường
                // Cấu trúc: Role:Username:Password:FullName
                String payload = selectedRole + ":" + username + ":" + password + ":" + fullName;
                client.networks.MessageDTO req = new client.networks.MessageDTO("REGISTER", payload);

                // 4. Gửi và nhận phản hồi
                client.networks.ClientMain.send(gson.toJson(req));
                String responseJson = client.networks.ClientMain.receive();

                Platform.runLater(() -> {
                    registerBtn.setDisable(false);
                    if (responseJson != null) {
                        client.networks.MessageDTO res = gson.fromJson(responseJson, client.networks.MessageDTO.class);

                        if ("REGISTER_SUCCESS".equals(res.getAction())) {
                            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đăng ký thành công!");
                            goToLogin(event);
                        } else {
                            // Hiển thị lỗi chi tiết từ Server (Ví dụ: Trùng tên đăng nhập)
                            showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", res.getPayload());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    registerBtn.setDisable(false);
                    showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối tới Server. Hãy chắc chắn Server đã bật!");
                });
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}