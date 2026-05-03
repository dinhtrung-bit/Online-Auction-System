package client.controllers;

import client.models.UserSession;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import com.google.gson.Gson;

public class LoginController {

    @FXML private Button btnAdmin;
    @FXML private Button btnSeller;
    @FXML private Button btnBidder;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    private String selectedRole = "Bidder";
    private Gson gson = new Gson();

    private final String IDLE_STYLE = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 5; -fx-background-radius: 5; -fx-text-fill: black;";
    private final String ACTIVE_STYLE = "-fx-background-color: #ecfdf5; -fx-text-fill: #10B981; -fx-border-color: #a7f3d0; -fx-border-radius: 5; -fx-background-radius: 5; -fx-font-weight: bold;";

    @FXML
    void selectAdmin(ActionEvent event) {
        selectedRole = "Admin";
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
        btnAdmin.setStyle(selectedRole.equals("Admin") ? ACTIVE_STYLE : IDLE_STYLE);
        btnSeller.setStyle(selectedRole.equals("Seller") ? ACTIVE_STYLE : IDLE_STYLE);
        btnBidder.setStyle(selectedRole.equals("Bidder") ? ACTIVE_STYLE : IDLE_STYLE);
    }

    @FXML
    void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        // Lấy đối tượng phát ra sự kiện (nút Đăng nhập) để vô hiệu hóa tạm thời
        Node loginButton = (Node) event.getSource();
        loginButton.setDisable(true);

        // 1. Đưa tiến trình gọi mạng (blocking I/O) xuống luồng chạy nền
        CompletableFuture.runAsync(() -> {
            try {
                // Kết nối tới Server
                client.networks.ClientMain.connectToServer();

                // Tạo gói tin gửi lên Server (Role:Username:Password)
                String payload = selectedRole + ":" + username + ":" + password;
                client.networks.MessageDTO req = new client.networks.MessageDTO("LOGIN", payload);

                // Gửi qua mạng
                client.networks.ClientMain.send(gson.toJson(req));

                // 2. Chờ Server trả lời (Lệnh này block luồng nền, không làm đơ giao diện UI)
                String responseJson = client.networks.ClientMain.receive();

                // 3. Cập nhật lại UI dựa trên kết quả - Bắt buộc đẩy vào Platform.runLater
                Platform.runLater(() -> {
                    loginButton.setDisable(false); // Mở lại nút bấm sau khi có kết quả

                    if (responseJson != null) {
                        client.networks.MessageDTO res = gson.fromJson(responseJson, client.networks.MessageDTO.class);

                        // 4. Nếu Server báo đúng thì mới cho qua
                        if ("LOGIN_SUCCESS".equals(res.getAction())) {
                            client.models.UserSession.username = username;
                            client.models.UserSession.role = selectedRole;

                            String fxmlPath = "";
                            if (selectedRole.equals("Bidder")) {
                                fxmlPath = "/client/views/auction-list.fxml";
                            } else if (selectedRole.equals("Seller")) {
                                fxmlPath = "/client/views/seller-dashboard.fxml";
                            } else if (selectedRole.equals("Admin")) {
                                fxmlPath = "/client/views/admin-dashboard.fxml";
                            }

                            try {
                                Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
                                Stage stage = (Stage) loginButton.getScene().getWindow();
                                stage.getScene().setRoot(root);
                                stage.setMaximized(true);
                            } catch (IOException e) {
                                e.printStackTrace();
                                showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể tải giao diện tiếp theo.");
                            }
                        } else {
                            // Server báo sai
                            showAlert(Alert.AlertType.ERROR, "Lỗi đăng nhập", "Sai thông tin: " + res.getPayload());
                        }
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Lỗi", "Không nhận được phản hồi từ Server.");
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                // Lỗi mạng hoặc Exception cũng phải thông báo lên UI qua runLater
                Platform.runLater(() -> {
                    loginButton.setDisable(false); // Mở lại nút
                    showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Mất kết nối với Server hoặc có lỗi xảy ra!");
                });
            }
        });
    }

    // Hàm hỗ trợ để tái sử dụng việc hiển thị hộp thoại Alert
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}