package client.controllers;

import client.models.UserSession;
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
import com.google.gson.Gson;
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

        // 1. Kết nối tới Server
        client.networks.ClientMain.connectToServer();

        // 2. Tạo gói tin gửi lên Server (Role:Username:Password)
        String payload = selectedRole + ":" + username + ":" + password;
        client.networks.MessageDTO req = new client.networks.MessageDTO("LOGIN", payload);

        // Gửi qua mạng
        client.networks.ClientMain.send(gson.toJson(req));

        try {
            // 3. Chờ Server trả lời
            String responseJson = client.networks.ClientMain.receive();
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

                    Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
                    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                    stage.getScene().setRoot(root);
                    stage.setMaximized(true);

                } else {
                    // Server báo sai
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi đăng nhập");
                    alert.setHeaderText(null);
                    alert.setContentText("Sai thông tin: " + res.getPayload());
                    alert.showAndWait();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Lỗi mất kết nối với Server!");
            alert.showAndWait();
        }
    }
}