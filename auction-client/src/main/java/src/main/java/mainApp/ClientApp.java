package src.main.java.mainApp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;

public class ClientApp extends Application {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private VBox root = new VBox(15);

    // UI Elements
    private TextField txtName = new TextField();
    private ComboBox<String> cbRole = new ComboBox<>();
    private ListView<String> lvItems = new ListView<>();
    private Label lblPrice = new Label("Giá: 0k");
    private TextField txtBid = new TextField();
    private Label lblStatus = new Label();

    private String currentRole = "";

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        showLogin();

        stage.setScene(new Scene(root, 350, 450));
        stage.setTitle("Auction AI");
        stage.show();
    }

    private void showLogin() {
        root.getChildren().clear();
        cbRole.getItems().setAll("BIDDER", "SELLER");
        cbRole.setValue("BIDDER");
        Button btn = new Button("Đăng nhập");
        btn.setOnAction(e -> connect());
        root.getChildren().addAll(new Label("Tên:"), txtName, new Label("Vai trò:"), cbRole, btn, lblStatus);
    }

    private void showLobby() {
        root.getChildren().clear();
        Button btnJoin = new Button("Vào Đấu Giá");
        btnJoin.setOnAction(e -> {
            String sel = lvItems.getSelectionModel().getSelectedItem();
            if(sel != null) {
                out.println("JOIN:" + sel);
                showAuction();
            }
        });
        root.getChildren().addAll(new Label("SẢNH CHỜ"), lvItems, btnJoin);
        out.println("LIST_REQ");
    }

    private void showAuction() {
        root.getChildren().clear();
        Button btnBid = new Button("ĐẶT GIÁ");
        btnBid.setOnAction(e -> out.println("BID:" + txtBid.getText()));
        Button btnBack = new Button("Quay lại");
        btnBack.setOnAction(e -> showLobby());
        root.getChildren().addAll(lblPrice, new Label("Nhập giá:"), txtBid, btnBid, lblStatus, btnBack);
    }

    private void showSeller() {
        root.getChildren().clear();
        TextField tName = new TextField(); tName.setPromptText("Tên hàng");
        TextField tPrice = new TextField(); tPrice.setPromptText("Giá");
        Button btn = new Button("Đăng bán");
        btn.setOnAction(e -> out.println("ADD_ITEM:" + tName.getText() + ":Mô tả:" + tPrice.getText()));
        root.getChildren().addAll(new Label("ĐĂNG BÁN"), tName, tPrice, btn, lblStatus);
    }

    private void connect() {
        try {
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            currentRole = cbRole.getValue();
            out.println("LOGIN:" + txtName.getText() + ":" + currentRole);

            if(currentRole.equals("SELLER")) showSeller(); else showLobby();
            new Thread(this::listen).start();
        } catch (Exception e) { lblStatus.setText("Lỗi kết nối!"); }
    }

    private void listen() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                String finalMsg = msg;
                Platform.runLater(() -> {
                    if (finalMsg.startsWith("ITEM_LIST:")) {
                        lvItems.getItems().setAll(finalMsg.substring(10).split(","));
                    } else if (finalMsg.startsWith("CURRENT_BID:") || finalMsg.startsWith("NEW_BID:")) {
                        String p = finalMsg.split(":")[finalMsg.split(":").length - 1];
                        lblPrice.setText("Giá hiện tại: " + p + "k");
                        lblStatus.setText("");
                    } else if (finalMsg.startsWith("ERROR:")) {
                        lblStatus.setText(finalMsg.substring(6));
                        lblStatus.setTextFill(Color.RED);
                    } else if (finalMsg.startsWith("SERVER_MSG:")) {
                        lblStatus.setText(finalMsg.split(":")[1]);
                        lblStatus.setTextFill(Color.GREEN);
                    }
                });
            }
        } catch (Exception e) {
            Platform.runLater(() -> lblStatus.setText("Mất kết nối!"));
        }
    }

    private void closeConnection() {
        try { if(socket != null) socket.close(); } catch (Exception e) {}
    }
}