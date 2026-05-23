package client.controllers;

import client.models.user.UserSession;
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
 * LoginController — màn hình đăng nhập.
 *
 * <p><b>Refactor v2:</b>
 * <ul>
 *   <li>Network gửi/nhận tách qua {@link ServerGateway} + {@link RequestResponse}.
 *   <li>Alert tách qua {@link Dialogs}.
 *   <li>Inline-style toggle button gom vào hằng số.
 * </ul>
 */
public class LoginController {

    @FXML private Button btnAdmin, btnSeller, btnBidder;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    private String selectedRole = "Bidder";

    private static final String IDLE_STYLE =
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

    private static final String ACTIVE_STYLE =
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
    public void initialize() {
        updateButtonStyles();
    }

    // ─── Role selection ─────────────────────────────────────────────

    @FXML void selectAdmin(ActionEvent event)  { selectedRole = "Admin";  updateButtonStyles(); }
    @FXML void selectSeller(ActionEvent event) { selectedRole = "Seller"; updateButtonStyles(); }
    @FXML void selectBidder(ActionEvent event) { selectedRole = "Bidder"; updateButtonStyles(); }

    private void updateButtonStyles() {
        btnAdmin.setStyle("Admin".equals(selectedRole)   ? ACTIVE_STYLE : IDLE_STYLE);
        btnSeller.setStyle("Seller".equals(selectedRole) ? ACTIVE_STYLE : IDLE_STYLE);
        btnBidder.setStyle("Bidder".equals(selectedRole) ? ACTIVE_STYLE : IDLE_STYLE);
    }

    // ─── Navigation ─────────────────────────────────────────────────

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

    // ─── Login flow ─────────────────────────────────────────────────

    @FXML
    void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Dialogs.warn("Cảnh báo", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        Node loginBtn = (Node) event.getSource();
        loginBtn.setDisable(true);

        Map<String, String> loginData = new LinkedHashMap<>();
        loginData.put("role",     selectedRole);
        loginData.put("username", username);
        loginData.put("password", password);

        ServerGateway.ensureConnected();
        RequestResponse.exchange()
                .request("LOGIN", new com.google.gson.Gson().toJson(loginData))
                .onSuccess(payload -> {
                    loginBtn.setDisable(false);

                    int userId = -1;
                    double balance = 0.0;

                    try {
                        java.lang.reflect.Type type =
                                new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType();

                        Map<String, Object> userData = new com.google.gson.Gson().fromJson(payload, type);

                        if (userData != null) {
                            Object idObj = userData.getOrDefault("userId", userData.get("id"));
                            if (idObj instanceof Number n) {
                                userId = n.intValue();
                            } else if (idObj != null) {
                                userId = (int) Double.parseDouble(String.valueOf(idObj));
                            }

                            Object balanceObj = userData.getOrDefault("accountBalance", userData.get("balance"));
                            if (balanceObj instanceof Number n) {
                                balance = n.doubleValue();
                            } else if (balanceObj != null) {
                                balance = Double.parseDouble(String.valueOf(balanceObj));
                            }
                        }
                    } catch (Exception ignored) {}

                    UserSession.getInstance().login(userId, username, selectedRole, balance);
                    switchScene(loginBtn);
                })
                .onFailed(payload -> {
                    loginBtn.setDisable(false);
                    Dialogs.error("Lỗi đăng nhập", "Sai thông tin: " + payload);
                })
                .onTimeout(() -> {
                    loginBtn.setDisable(false);
                    Dialogs.error("Hết thời gian chờ",
                            "Server không phản hồi. Kiểm tra kết nối và thử lại.");
                })
                .send();
    }

    private void switchScene(Node node) {
        try {
            String fxmlPath = switch (selectedRole) {
                case "Bidder" -> "/client/views/auction-list.fxml";
                case "Seller" -> "/client/views/seller-dashboard.fxml";
                default       -> "/client/views/admin-dashboard.fxml";
            };
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) node.getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.setMaximized(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
