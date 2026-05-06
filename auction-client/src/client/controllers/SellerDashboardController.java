package client.controllers;

import client.models.Item;
import client.models.UserSession; // Đã thêm import này
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
    private final Gson gson = new Gson();

    @FXML
    public void initialize() {
        itemList = FXCollections.observableArrayList();
        tableItems.setItems(itemList);

        Label emptyLabel = new Label("Kho hàng đang trống. Hãy thêm sản phẩm mới!");
        emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-style: italic;");
        tableItems.setPlaceholder(emptyLabel);

        colId.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));

        colWinner.setText("Thông tin chi tiết");
        colWinner.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDetails()));

        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty("Chưa bắt đầu"));
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add("badge-success");
                setGraphic(badge);
            }
        });

        // Load danh sách sản phẩm của Seller ngay khi vào trang
        registerMyItemsListener();

        // ĐÃ NÂNG CẤP: Truyền username của người dùng hiện tại lên Server thay vì chuỗi rỗng
        String currentUser = (UserSession.username != null) ? UserSession.username : "";
        ClientMain.send(gson.toJson(new MessageDTO("GET_MY_ITEMS", currentUser)));
    }

    // ─── Load sản phẩm từ Server ──────────────────────────────────────────────

    private void registerMyItemsListener() {
        ClientMain.registerListener("MY_ITEMS", payload -> {
            try {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                List<Map<String, Object>> raw = gson.fromJson(payload, listType);
                Platform.runLater(() -> {
                    itemList.clear();
                    if (raw != null) {
                        for (Map<String, Object> m : raw) {
                            String id    = String.valueOf(((Number) m.get("itemId")).intValue());
                            String name  = (String) m.get("name");
                            double price = ((Number) m.get("startingPrice")).doubleValue();
                            String cat   = String.valueOf(m.getOrDefault("category", ""));
                            // Tái sử dụng Art để hiện trong bảng
                            client.models.Art displayItem = new client.models.Art(id, name, price,
                                    String.valueOf(m.getOrDefault("description", "")));
                            itemList.add(displayItem);
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("Lỗi parse MY_ITEMS: " + e.getMessage());
            }
        });
    }

    // ─── Thêm sản phẩm ───────────────────────────────────────────────────────

    @FXML
    private void showAddProductDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/client/views/add-product-dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Thêm Sản Phẩm Mới");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(root));
            dialogStage.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialogStage.showAndWait();

            AddProductController controller = loader.getController();
            Item newItem = controller.getResultItem();
            if (newItem == null) return; // User bấm Cancel

            // Bước 1: Đăng ký lắng nghe phản hồi ADD_ITEM từ Server
            ClientMain.registerListener("ADD_ITEM_SUCCESS", payload -> {
                ClientMain.unregisterListener("ADD_ITEM_SUCCESS");
                ClientMain.unregisterListener("ADD_ITEM_FAILED");

                // payload = itemId do DB tự sinh
                int newItemId;
                try { newItemId = Integer.parseInt(payload.trim()); }
                catch (NumberFormatException e) { newItemId = -1; }
                final int finalItemId = newItemId;

                Platform.runLater(() -> {
                    itemList.add(newItem); // Cập nhật UI ngay

                    if (finalItemId <= 0) {
                        showAlert("Cảnh báo",
                                "Sản phẩm đã lưu nhưng không lấy được ID. Không thể tạo phòng đấu giá tự động.");
                        return;
                    }

                    // Bước 2: Hỏi Seller muốn tạo phòng đấu giá ngay không
                    showCreateAuctionDialog(finalItemId, newItem.getName());
                });
            });

            ClientMain.registerListener("ADD_ITEM_FAILED", payload -> {
                ClientMain.unregisterListener("ADD_ITEM_SUCCESS");
                ClientMain.unregisterListener("ADD_ITEM_FAILED");
                Platform.runLater(() -> showAlert("Lỗi", "Thêm sản phẩm thất bại: " + payload));
            });

            // Gửi ADD_ITEM lên Server
            ClientMain.send(gson.toJson(new MessageDTO("ADD_ITEM", gson.toJson(newItem))));

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể mở form thêm sản phẩm.");
        }
    }

    /**
     * Dialog hỏi Seller chọn thời lượng phiên đấu giá.
     * Sau khi xác nhận → gửi CREATE_AUCTION lên Server.
     */
    private void showCreateAuctionDialog(int itemId, String itemName) {
        // Dùng ChoiceDialog để chọn thời lượng nhanh
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                "30 phút",
                "10 phút", "30 phút", "1 giờ", "2 giờ", "6 giờ", "12 giờ", "24 giờ"
        );
        dialog.setTitle("Tạo phòng đấu giá");
        dialog.setHeaderText("Sản phẩm \"" + itemName + "\" đã lưu thành công!");
        dialog.setContentText("Chọn thời lượng đấu giá:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return; // Seller bấm Cancel — không tạo phòng

        int durationMinutes = parseDuration(result.get());

        // Đăng ký listener phản hồi CREATE_AUCTION
        ClientMain.registerListener("CREATE_AUCTION_SUCCESS", payload -> {
            ClientMain.unregisterListener("CREATE_AUCTION_SUCCESS");
            ClientMain.unregisterListener("CREATE_AUCTION_FAILED");
            Platform.runLater(() -> showInfo("Thành công",
                    "Phòng đấu giá cho \"" + itemName + "\" đã được tạo!\n" +
                            "Bidder sẽ thấy sản phẩm trong danh sách ngay bây giờ."));
        });

        ClientMain.registerListener("CREATE_AUCTION_FAILED", payload -> {
            ClientMain.unregisterListener("CREATE_AUCTION_SUCCESS");
            ClientMain.unregisterListener("CREATE_AUCTION_FAILED");
            Platform.runLater(() -> showAlert("Lỗi tạo phòng",
                    "Sản phẩm đã lưu nhưng tạo phòng thất bại: " + payload));
        });

        // Gửi CREATE_AUCTION: payload = "itemId:durationMinutes"
        ClientMain.send(gson.toJson(
                new MessageDTO("CREATE_AUCTION", itemId + ":" + durationMinutes)));
    }

    /** Chuyển chuỗi lựa chọn thành số phút */
    private int parseDuration(String choice) {
        return switch (choice) {
            case "10 phút"  -> 10;
            case "30 phút"  -> 30;
            case "1 giờ"    -> 60;
            case "2 giờ"    -> 120;
            case "6 giờ"    -> 360;
            case "12 giờ"   -> 720;
            case "24 giờ"   -> 1440;
            default          -> 30;
        };
    }

    // ─── Sửa sản phẩm ────────────────────────────────────────────────────────

    @FXML
    private void handleEditProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm để chỉnh sửa!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/client/views/add-product-dialog.fxml"));
            Parent root = loader.load();
            AddProductController controller = loader.getController();
            controller.setEditData(selectedItem);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Chỉnh sửa Sản Phẩm");
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

            Item updatedItem = controller.getResultItem();
            if (updatedItem == null) return;

            // Gửi UPDATE_ITEM lên Server
            String payload = selectedItem.getItemId() + ":" + updatedItem.getName()
                    + ":" + updatedItem.getDetails() + ":ART:" + updatedItem.getStartingPrice();
            ClientMain.send(gson.toJson(new MessageDTO("UPDATE_ITEM", payload)));

            // Cập nhật UI ngay
            int index = itemList.indexOf(selectedItem);
            if (index >= 0) itemList.set(index, updatedItem);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleEditProduct(ActionEvent event) { handleEditProduct(); }

    // ─── Xóa sản phẩm ────────────────────────────────────────────────────────

    @FXML
    private void handleDeleteProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm để xóa!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText("Xóa sản phẩm: " + selectedItem.getName());
        confirm.setContentText("Bạn có chắc chắn muốn xóa? Thao tác này không thể hoàn tác.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // Đăng ký listener trước khi gửi
        ClientMain.registerListener("DELETE_ITEM_SUCCESS", payload -> {
            ClientMain.unregisterListener("DELETE_ITEM_SUCCESS");
            ClientMain.unregisterListener("DELETE_ITEM_FAILED");
            Platform.runLater(() -> itemList.remove(selectedItem)); // Xóa UI sau khi Server xác nhận
        });

        ClientMain.registerListener("DELETE_ITEM_FAILED", payload -> {
            ClientMain.unregisterListener("DELETE_ITEM_SUCCESS");
            ClientMain.unregisterListener("DELETE_ITEM_FAILED");
            Platform.runLater(() -> showAlert("Xóa thất bại", payload));
        });

        ClientMain.send(gson.toJson(
                new MessageDTO("DELETE_ITEM", selectedItem.getItemId())));
    }

    // ─── Đăng xuất ───────────────────────────────────────────────────────────

    @FXML
    private void handleLogout() {
        ClientMain.unregisterListener("MY_ITEMS");
        try {
            Scene currentScene = tableItems.getScene();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/client/views/login.fxml"));
            currentScene.setRoot(loader.load());
            ((Stage) currentScene.getWindow()).setTitle("Đăng nhập - AuctionVN");
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể tải màn hình đăng nhập!");
        }
    }

    // ─── Helpers UI ──────────────────────────────────────────────────────────

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content);
        alert.showAndWait();
    }
}