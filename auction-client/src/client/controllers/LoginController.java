<<<<<<< HEAD
<<<<<<< HEAD:auction-client/src/main/java/com/auctions/client/controllers/LoginController.java
package com.auctions.client.controllers;
=======
package client.controllers;
>>>>>>> main:auction-client/src/client/controllers/LoginController.java
=======
package src.client.controllers;
>>>>>>> e6fdc0c29820aeb4b000a79a18715e91b6bec51e

import com.auctions.client.ClientApp;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.fxml.FXML;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText("Vui lòng nhập đầy đủ username và password");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    ClientApp.class.getResource("/views/auction-list.fxml")
            );
            Scene scene = new Scene(loader.load(), 1000, 650);
            scene.getStylesheets().add(
                    ClientApp.class.getResource("/src/client/styles/app.css").toExternalForm()
            );

            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Auction List");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText("Chưa làm màn hình Register");
        alert.showAndWait();
    }
}