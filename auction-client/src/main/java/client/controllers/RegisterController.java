package client.controllers;

import client.services.RequestResponse;
import client.services.ServerGateway;
import client.utils.dialogs.Dialogs;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RegisterController — màn hình đăng ký.
 *
 * <p><b>Refactor v2:</b>
 * <ul>
 *   <li>Network qua {@link ServerGateway} + {@link RequestResponse}.
 *   <li>Alert qua {@link Dialogs}.
 *   <li>Loại bỏ inline {@code java.util.Timer} thủ công.
 * </ul>
 */
public class RegisterController {

    @FXML private Button btnSeller, btnBidder;
    @FXML private TextField txtFullName, txtUsername;
    @FXML private PasswordField txtPassword;

    private String selectedRole = "Bidder";

    private static final String IDLE_STYLE =
            "-fx-background-color: white; -fx-border-color: #D1D5DB; -fx-border-radius: 8;"
                    + " -fx-background-radius: 8; -fx-cursor: hand; -fx-text-fill: #374151;";
    private static final String ACTIVE_STYLE =
            "-fx-background-color: #ECFDF5; -fx-text-fill: #10B981; -fx-border-color: #34D399;"
                    + " -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8;"
                    + " -fx-font-weight: bold; -fx-cursor: hand;";

    @FXML
    public void initialize() {
        updateButtonStyles();
    }

    @FXML void selectSeller(ActionEvent event) { selectedRole = "Seller"; updateButtonStyles(); }
    @FXML void selectBidder(ActionEvent event) { selectedRole = "Bidder"; updateButtonStyles(); }

    private void updateButtonStyles() {
        btnSeller.setStyle("Seller".equals(selectedRole) ? ACTIVE_STYLE : IDLE_STYLE);
        btnBidder.setStyle("Bidder".equals(selectedRole) ? ACTIVE_STYLE : IDLE_STYLE);
    }

    @FXML
    void goToLogin(ActionEvent event) {
        ServerGateway.off("REGISTER_SUCCESS", "REGISTER_FAILED");
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

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Dialogs.warn("Cảnh báo", "Vui lòng điền đủ thông tin!");
            return;
        }

        Node registerBtn = (Node) event.getSource();
        registerBtn.setDisable(true);

        Map<String, String> regData = new LinkedHashMap<>();
        regData.put("username", username);
        regData.put("password", password);
        regData.put("role",     selectedRole);
        regData.put("fullName", fullName);

        ServerGateway.ensureConnected();
        RequestResponse.exchange()
                .request("REGISTER", new com.google.gson.Gson().toJson(regData))
                .onSuccess(payload -> {
                    registerBtn.setDisable(false);
                    Dialogs.info("Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
                    goToLogin(event);
                })
                .onFailed(payload -> {
                    registerBtn.setDisable(false);
                    Dialogs.error("Lỗi đăng ký", payload);
                })
                .onTimeout(() -> {
                    registerBtn.setDisable(false);
                    Dialogs.error("Hết thời gian chờ", "Server không phản hồi. Vui lòng thử lại.");
                })
                .send();
    }
}
