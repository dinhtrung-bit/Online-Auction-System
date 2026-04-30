package client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AuctionListController {

    @FXML
    void viewDetail(ActionEvent event) {
        switchScene(event, "/client/views/auction-detail.fxml");
    }

    @FXML
    void handleLogout(ActionEvent event) {
        switchScene(event, "/client/views/login.fxml");
    }

    private void switchScene(ActionEvent event, String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();



            boolean isMax = stage.isMaximized();


            Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
            stage.setScene(scene);


            if (isMax) {
                stage.setMaximized(false);
                stage.setMaximized(true);
            }


            stage.setMaximized(true);

            stage.show();
        } catch (Exception e) {
            System.err.println("Lỗi load file FXML: " + fxmlPath);
            e.printStackTrace();
        }
    }
}