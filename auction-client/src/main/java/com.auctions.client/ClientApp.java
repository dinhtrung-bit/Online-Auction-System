<<<<<<< HEAD:auction-client/src/client/ClientApp/ClientApp.java
<<<<<<< HEAD
package client.ClientApp;
=======
package src.client.ClientApp;
>>>>>>> e6fdc0c29820aeb4b000a79a18715e91b6bec51e
=======
package com.auctions.client;
>>>>>>> main:auction-client/src/main/java/com.auctions.client/ClientApp.java

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                ClientApp.class.getResource("/views/login.fxml")
        );

        Scene scene = new Scene(loader.load(), 900, 600);
        scene.getStylesheets().add(
<<<<<<< HEAD
                ClientApp.class.getResource("/styles/app.css").toExternalForm()
=======
                ClientApp.class.getResource("/src/client/styles/app.css").toExternalForm()
>>>>>>> e6fdc0c29820aeb4b000a79a18715e91b6bec51e
        );

        stage.setTitle("Online Auction System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}