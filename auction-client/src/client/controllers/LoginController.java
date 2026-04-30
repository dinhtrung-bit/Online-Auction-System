package client.controllers;

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

public class LoginController {


    @FXML private Button btnAdmin;
    @FXML private Button btnSeller;
    @FXML private Button btnBidder;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;


    private String selectedRole = "Bidder";


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

        boolean isValid = false;


        if (selectedRole.equals("Admin") && username.equals("admin") && password.equals("admin123")) {
            isValid = true;
        } else if (selectedRole.equals("Seller") && username.equals("seller1") && password.equals("seller123")) {
            isValid = true;
        } else if (selectedRole.equals("Bidder") && username.equals("bidder1") && password.equals("bidder123")) {
            isValid = true;
        }

        if (isValid) {

            try {


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


                if (!stage.isMaximized()) {
                    stage.setMaximized(true);
                }

                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi đăng nhập");
            alert.setHeaderText(null);
            alert.setContentText("Sai tài khoản, mật khẩu hoặc vai trò. Vui lòng kiểm tra lại thông tin Demo!");
            alert.showAndWait();
        }
    }
}