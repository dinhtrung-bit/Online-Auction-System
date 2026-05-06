package client.controllers;

import client.models.Item;
import client.models.UserSession;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.event.ActionEvent;

public class SellerDashboardController {

    @FXML private TableView<Item> tableItems;
    @FXML private TableColumn<Item, String> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, String> colWinner;
    @FXML private TableColumn<Item, String> colStatus;

    private ObservableList<Item> itemList;
    private Gson gson = new Gson();

    // Map itemId -> auction info (status, currentPrice)
    private Map<String, Map<String, Object>> auctionMap = new HashMap<>();

    @FXML
    public void initialize() {
        itemList = FXCollections.observableArrayList();
        tableItems.setItems(itemList);

        Label emptyLabel = new Label("Kho hàng đang trống. Hãy thêm sản phẩm mới!");
        emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-style: italic;");
        tableItems.setPlaceholder(emptyLabel);

        colId.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Giá hiện tại — lấy từ auctionMap nếu có
        colPrice.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            Map<String, Object> auction = auctionMap.get(item.getItemId());
            double price = auction != null
                    ? Double.parseDouble(auction.get("currentPrice").toString())
                    : item.getStartingPrice();
            return new javafx.beans.property.SimpleObjectProperty<>(price);
        });

        colWinner.setText("Thông tin chi tiết");
        colWinner.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDetails()));

        // Trạng thái — lấy từ auctionMap
        colStatus.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            Map<String, Object> auction = auctionMap.get(item.getItemId());
            String status = auction != null ? (String) auction.get("status") : "NONE";
            return new SimpleStringProperty(statusToText(status));
        });

        colStatus.setCellFactory(column -> new TableCell<Item, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                Label badge = new Label(status);
                badge.setStyle(stylForStatus(status));
                setGraphic(badge);
            }
        });

        // Lắng nghe broadcast AUCTION_STARTED và AUCTION_FINISHED để cập nhật realtime
        ClientMain.registerListener("AUCTION_STARTED", payload ->
                Platform.runLater(this::loadMyAuctionsFromServer));
        ClientMain.registerListener("AUCTION_FINISHED", payload ->
                Platform.runLater(this::loadMyAuctionsFromServer));

        loadMyItemsFromServer();
    }

    private String statusToText(String status) {
        return switch (status) {
            case "OPEN"     -> "⏳ Sắp bắt đầu";
            case "RUNNING"  -> "🔴 Đang đấu giá";
            case "FINISHED" -> "✅ Kết thúc";
            case "PAID"     -> "💰 Đã thanh toán";
            case "CANCELED" -> "❌ Đã hủy";
            default         -> "📦 Chưa đăng";
        };
    }

    private String stylForStatus(String status) {
        String base = "-fx-padding: 3 8; -fx-background-radius: 5; -fx-font-weight: bold; -fx-font-size: 11px;";
        return switch (status) {
            case "🔴 Đang đấu giá"  -> base + "-fx-background-color: #dcfce7; -fx-text-fill: #166534;";
            case "⏳ Sắp bắt đầu"   -> base + "-fx-background-color: #fef9c3; -fx-text-fill: #854d0e;";
            case "✅ Kết thúc"       -> base + "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";
            case "💰 Đã thanh toán"  -> base + "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af;";
            case "❌ Đã hủy"         -> base + "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
            default                  -> base + "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";
        };
    }

    private void loadMyItemsFromServer() {
        ClientMain.registerListener("MY_ITEMS", payload -> {
            ClientMain.unregisterListener("MY_ITEMS");
            try {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                List<Map<String, Object>> list = gson.fromJson(payload, listType);
                Platform.runLater(() -> {
                    itemList.clear();
                    if (list != null) {
                        for (Map<String, Object> m : list) {
                            String itemId = m.get("itemId") != null ? m.get("itemId").toString() : "";
                            String name = (String) m.getOrDefault("name", "");
                            String description = (String) m.getOrDefault("description", "");
                            double price = m.get("startingPrice") != null
                                    ? Double.parseDouble(m.get("startingPrice").toString()) : 0;
                            Item item = new client.models.Art(itemId, name, price, description);
                            itemList.add(item);
                        }
                    }
                    // Sau khi load items xong, load auction status
                    loadMyAuctionsFromServer();
                });
            } catch (Exception e) {
                System.err.println("Lỗi parse MY_ITEMS: " + e.getMessage());
            }
        });
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("GET_MY_ITEMS", "")))).start();
    }

    private void loadMyAuctionsFromServer() {
        ClientMain.registerListener("MY_AUCTIONS", payload -> {
            ClientMain.unregisterListener("MY_AUCTIONS");
            try {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                List<Map<String, Object>> list = gson.fromJson(payload, listType);
                Platform.runLater(() -> {
                    auctionMap.clear();
                    if (list != null) {
                        for (Map<String, Object> a : list) {
                            String itemId = a.get("itemId") != null
                                    ? a.get("itemId").toString() : "";
                            auctionMap.put(itemId, a);
                        }
                    }
                    tableItems.refresh(); // Cập nhật lại bảng
                });
            } catch (Exception e) {
                System.err.println("Lỗi parse MY_AUCTIONS: " + e.getMessage());
            }
        });
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("GET_MY_AUCTIONS", "")))).start();
    }

    @FXML
    private void showAddProductDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/add-product-dialog.fxml"));
            Parent root = loader.load();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Thêm Sản Phẩm Mới");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(root));
            dialogStage.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialogStage.showAndWait();

            AddProductController controller = loader.getController();
            Item newItem = controller.getResultItem();

            if (newItem != null) {
                ClientMain.registerListener("ADD_ITEM_SUCCESS", payload -> {
                    ClientMain.unregisterListener("ADD_ITEM_SUCCESS");
                    ClientMain.unregisterListener("ADD_ITEM_FAILED");
                    Platform.runLater(() -> {
                        showAlert("Thành công", "Đã thêm sản phẩm!");
                        loadMyItemsFromServer();
                    });
                });
                ClientMain.registerListener("ADD_ITEM_FAILED", payload -> {
                    ClientMain.unregisterListener("ADD_ITEM_SUCCESS");
                    ClientMain.unregisterListener("ADD_ITEM_FAILED");
                    Platform.runLater(() -> showAlert("Lỗi", "Thêm thất bại: " + payload));
                });
                ClientMain.send(gson.toJson(new MessageDTO("ADD_ITEM", gson.toJson(newItem))));
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể mở form thêm sản phẩm.");
        }
    }

    @FXML
    private void handleStartAuction() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn sản phẩm để tạo phiên đấu giá!");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Tạo phiên đấu giá");
        dialog.setHeaderText("Sản phẩm: " + selectedItem.getName());

        ButtonType btnOk = new ButtonType("Bắt đầu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnOk, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        DatePicker datePicker = new DatePicker(java.time.LocalDate.now());
        TextField txtTime = new TextField(java.time.LocalTime.now()
                .plusMinutes(5).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        TextField txtDuration = new TextField("30");

        grid.add(new Label("Ngày bắt đầu:"), 0, 0);
        grid.add(datePicker, 1, 0);
        grid.add(new Label("Giờ bắt đầu (HH:mm):"), 0, 1);
        grid.add(txtTime, 1, 1);
        grid.add(new Label("Thời gian đấu giá (phút):"), 0, 2);
        grid.add(txtDuration, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn == btnOk
                ? datePicker.getValue() + "T" + txtTime.getText() + ":" + txtDuration.getText()
                : null);

        dialog.showAndWait().ifPresent(data -> {
            String[] parts = data.split(":");
            String startTime = parts[0] + ":" + parts[1];
            String duration  = parts[2];
            String payload   = selectedItem.getItemId() + ":" + startTime + ":" + duration;

            ClientMain.registerListener("CREATE_AUCTION_SUCCESS", p -> {
                ClientMain.unregisterListener("CREATE_AUCTION_SUCCESS");
                ClientMain.unregisterListener("CREATE_AUCTION_FAILED");
                Platform.runLater(() -> {
                    showAlert("Thành công", "Phiên đấu giá sẽ bắt đầu lúc "
                            + startTime.replace("T", " "));
                    loadMyAuctionsFromServer();
                });
            });
            ClientMain.registerListener("CREATE_AUCTION_FAILED", p -> {
                ClientMain.unregisterListener("CREATE_AUCTION_SUCCESS");
                ClientMain.unregisterListener("CREATE_AUCTION_FAILED");
                Platform.runLater(() -> showAlert("Lỗi", "Tạo phiên thất bại: " + p));
            });

            new Thread(() -> ClientMain.send(gson.toJson(
                    new MessageDTO("CREATE_AUCTION", payload)))).start();
        });
    }

    @FXML
    private void handleEditProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm để chỉnh sửa!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/add-product-dialog.fxml"));
            Parent root = loader.load();
            AddProductController controller = loader.getController();
            controller.setEditData(selectedItem);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Chỉnh sửa Sản Phẩm");
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

            Item updatedItem = controller.getResultItem();
            if (updatedItem != null) {
                int index = itemList.indexOf(selectedItem);
                itemList.set(index, updatedItem);
                ClientMain.send(gson.toJson(new MessageDTO("UPDATE_ITEM",
                        updatedItem.getItemId() + ":" + updatedItem.getName() + ":"
                                + updatedItem.getDetails() + ":ART:" + updatedItem.getStartingPrice())));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeleteProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm để xóa!");
            return;
        }
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Xác nhận xóa");
        confirmDialog.setHeaderText("Xóa sản phẩm: " + selectedItem.getName());
        confirmDialog.setContentText("Bạn có chắc chắn muốn xóa?");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            ClientMain.send(gson.toJson(new MessageDTO("DELETE_ITEM", selectedItem.getItemId())));
            itemList.remove(selectedItem);
        }
    }

    @FXML
    private void handleLogout() {
        ClientMain.unregisterListener("AUCTION_STARTED");
        ClientMain.unregisterListener("AUCTION_FINISHED");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/login.fxml"));
            Parent root = loader.load();
            Scene currentScene = tableItems.getScene();
            currentScene.setRoot(root);
            Stage stage = (Stage) currentScene.getWindow();
            stage.setTitle("Đăng nhập - AuctionVN");
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể tải màn hình đăng nhập!");
        }
    }

    @FXML
    void handleEditProduct(ActionEvent event) { handleEditProduct(); }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}