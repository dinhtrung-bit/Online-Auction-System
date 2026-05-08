package client.controllers;

import client.models.user.UserSession;
import client.networks.ClientMain;
import client.networks.MessageDTO;
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

public class LoginController {

    @FXML private Button btnAdmin, btnSeller, btnBidder;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    private String selectedRole = "Bidder";
    private final Gson gson = new Gson();

    private final String IDLE_STYLE =
            "-fx-background-color: white;" +
                    "-fx-border-color: #CBD5E1;" +
                    "-fx-border-width: 1.5;" +
                    "-fx-border-radius: 12;" +
                    "-fx-background-radius: 12;" +
                    "-fx-text-fill: #334155;" +
                    "-fx-font-weight: bold;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 0;" +
                    "-fx-min-height: 44;" +
                    "-fx-pref-height: 44;";

    private final String ACTIVE_STYLE =
            "-fx-background-color: #ECFDF5;" +
                    "-fx-border-color: #10B981;" +
                    "-fx-border-width: 2;" +
                    "-fx-border-radius: 12;" +
                    "-fx-background-radius: 12;" +
                    "-fx-text-fill: #059669;" +
                    "-fx-font-weight: bold;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 0;" +
                    "-fx-min-height: 44;" +
                    "-fx-pref-height: 44;";

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
    void goToRegister(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/register.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        Node loginBtn = (Node) event.getSource();
        loginBtn.setDisable(true);

        ClientMain.registerListener("LOGIN_SUCCESS", payload -> {
            ClientMain.unregisterListener("LOGIN_SUCCESS");
            ClientMain.unregisterListener("LOGIN_FAILED");

            Platform.runLater(() -> {
                loginBtn.setDisable(false);
                UserSession.username = username;
                UserSession.role = selectedRole;
                switchScene(loginBtn);
            });
        });

        ClientMain.registerListener("LOGIN_FAILED", payload -> {
            ClientMain.unregisterListener("LOGIN_SUCCESS");
            ClientMain.unregisterListener("LOGIN_FAILED");

            Platform.runLater(() -> {
                loginBtn.setDisable(false);
                showAlert(Alert.AlertType.ERROR, "Lỗi đăng nhập", "Sai thông tin: " + payload);
            });
        });

        CompletableFuture.runAsync(() -> {
            ClientMain.connectToServer();

            String payload = selectedRole + ":" + username + ":" + password;

            ClientMain.send(gson.toJson(
                    new MessageDTO("LOGIN", payload)
            ));
        });
    }

    private void switchScene(Node node) {
        try {
            String fxmlPath = selectedRole.equals("Bidder")
                    ? "/client/views/auction-list.fxml"
                    : selectedRole.equals("Seller")
                    ? "/client/views/seller-dashboard.fxml"
                    : "/client/views/admin-dashboard.fxml";

            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));

            Stage stage = (Stage) node.getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.setMaximized(true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void initialize() {
        updateButtonStyles();
    }
}