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

    private String selectedRole = "Bidder";
    private Gson gson = new Gson();

    private final String IDLE_STYLE = "-fx-background-color: white; -fx-border-color: #D1D5DB; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-text-fill: #374151;";
    private final String ACTIVE_STYLE = "-fx-background-color: #ECFDF5; -fx-text-fill: #10B981; -fx-border-color: #34D399; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-cursor: hand;";

    @FXML
    public void initialize() {
        updateButtonStyles();
    }

    @FXML
    void selectSeller(ActionEvent event) { selectedRole = "Seller"; updateButtonStyles(); }

    @FXML
    void selectBidder(ActionEvent event) { selectedRole = "Bidder"; updateButtonStyles(); }

    private void updateButtonStyles() {
        btnSeller.setStyle(selectedRole.equals("Seller") ? ACTIVE_STYLE : IDLE_STYLE);
        btnBidder.setStyle(selectedRole.equals("Bidder") ? ACTIVE_STYLE : IDLE_STYLE);
    }

    @FXML
    void goToLogin(ActionEvent event) {
        // Cleanup listener nếu người dùng nhấn back giữa chừng
        client.networks.ClientMain.unregisterListener("REGISTER_SUCCESS");
        client.networks.ClientMain.unregisterListener("REGISTER_FAILED");

        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    void handleRegister(ActionEvent event) {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đủ thông tin!");
            return;
        }

        Node registerBtn = (Node) event.getSource();
        registerBtn.setDisable(true);

        // Timeout: tự cleanup nếu sau 10 giây không có response
        java.util.concurrent.atomic.AtomicBoolean handled = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.Timer timeoutTimer = new java.util.Timer(true);
        timeoutTimer.schedule(new java.util.TimerTask() {
            @Override public void run() {
                if (handled.compareAndSet(false, true)) {
                    client.networks.ClientMain.unregisterListener("REGISTER_SUCCESS");
                    client.networks.ClientMain.unregisterListener("REGISTER_FAILED");
                    Platform.runLater(() -> {
                        registerBtn.setDisable(false);
                        showAlert(Alert.AlertType.ERROR, "Hết thời gian chờ",
                                "Server không phản hồi. Vui lòng thử lại.");
                    });
                }
            }
        }, 10_000);

        client.networks.ClientMain.registerListener("REGISTER_SUCCESS", payload -> {
            if (!handled.compareAndSet(false, true)) return;
            timeoutTimer.cancel();
            client.networks.ClientMain.unregisterListener("REGISTER_SUCCESS");
            client.networks.ClientMain.unregisterListener("REGISTER_FAILED");
            Platform.runLater(() -> {
                registerBtn.setDisable(false);
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
                goToLogin(event);
            });
        });

        client.networks.ClientMain.registerListener("REGISTER_FAILED", payload -> {
            if (!handled.compareAndSet(false, true)) return;
            timeoutTimer.cancel();
            client.networks.ClientMain.unregisterListener("REGISTER_SUCCESS");
            client.networks.ClientMain.unregisterListener("REGISTER_FAILED");
            Platform.runLater(() -> {
                registerBtn.setDisable(false);
                showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", payload);
            });
        });

        CompletableFuture.runAsync(() -> {
            client.networks.ClientMain.connectToServer();
            // [Fix] JSON payload thay vì split(":") — an toàn khi dữ liệu chứa ":"
            java.util.Map<String,String> regData = new java.util.LinkedHashMap<>();
            regData.put("username", username);
            regData.put("password", password);
            regData.put("role",     selectedRole);
            regData.put("fullName", fullName);
            String payload = gson.toJson(regData);
            client.networks.ClientMain.send(gson.toJson(
                    new client.networks.MessageDTO("REGISTER", payload)
            ));
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