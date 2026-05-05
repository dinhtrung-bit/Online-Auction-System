package client.controllers;

import client.models.Item;
import client.networks.MessageDTO;
import com.google.gson.Gson;
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
import java.util.Optional;
import javafx.event.ActionEvent;

public class SellerDashboardController {

    // Ánh xạ các thành phần từ file FXML
    @FXML private TableView<Item> tableItems;
    @FXML private TableColumn<Item, String> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, String> colWinner;
    @FXML private TableColumn<Item, String> colStatus;

    // ObservableList giúp bảng tự động cập nhật khi có dữ liệu mới
    private ObservableList<Item> itemList;
    private Gson gson = new Gson();

    @FXML
    public void initialize() {
        // 1. Khởi tạo danh sách và gắn vào bảng
        itemList = FXCollections.observableArrayList();
        tableItems.setItems(itemList);

        // 2. Định nghĩa giao diện khi bảng trống
        Label emptyLabel = new Label("Kho hàng đang trống. Hãy thêm sản phẩm mới!");
        emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-style: italic;");
        tableItems.setPlaceholder(emptyLabel);

        // 3. Liên kết dữ liệu (Data Binding) dựa vào các Getter trong Item.java
        colId.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));

        // 4. HIỂN THỊ ĐA HÌNH (Tính năng ăn điểm OOP):
        // Mượn cột colWinner để hiển thị thông tin động (Tác giả hoặc Tháng bảo hành)
        colWinner.setText("Thông tin chi tiết"); // Đổi tên cột trên giao diện
        colWinner.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDetails()));

        // 5. CUSTOM UI (Tính năng ăn điểm UX): Vẽ "Huy hiệu" (Badge) cho Trạng thái
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty("Chưa bắt đầu")); // Giả lập trạng thái
        colStatus.setCellFactory(column -> new TableCell<Item, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Tạo một nhãn Label và ốp class CSS badge-success của bạn vào
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge-success");
                    setGraphic(badge);
                }
            }
        });
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

            // Xóa background mặc định của Stage để CSS bo góc hoạt động mượt hơn
            dialogStage.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);

            dialogStage.showAndWait();

            // Nhận kết quả trả về từ Popup
            AddProductController controller = loader.getController();
            Item newItem = controller.getResultItem();

            if (newItem != null) {
                // Đóng gói thành JSON gửi lên Server (Chờ ghép code)
                String payloadJSON = gson.toJson(newItem);
                MessageDTO request = new MessageDTO("ADD_ITEM", payloadJSON);
                System.out.println("Sẵn sàng gửi Server: " + request.getAction() + " | " + request.getPayload());

                // Thêm vào danh sách -> Giao diện tự động cập nhật ngay lập tức
                itemList.add(newItem);
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể mở form thêm sản phẩm.");
        }
    }
    @FXML
    private void handleEditProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm trong bảng để chỉnh sửa!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/add-product-dialog.fxml"));
            Parent root = loader.load();

            // Lấy Controller và truyền dữ liệu sản phẩm hiện tại vào form
            AddProductController controller = loader.getController();
            controller.setEditData(selectedItem); // GỌI HÀM NÀY ĐỂ BẬT CHẾ ĐỘ SỬA

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Chỉnh sửa Sản Phẩm");
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

            // Nhận kết quả sau khi chỉnh sửa
            Item updatedItem = controller.getResultItem();
            if (updatedItem != null) {
                // Cập nhật lại giao diện bảng (Xóa cũ, thêm mới vào đúng vị trí)
                int index = itemList.indexOf(selectedItem);
                itemList.set(index, updatedItem);

                // TODO: Chỗ này sẽ ghép code Gửi gói tin UPDATE_ITEM lên Server sau
                System.out.println("Đã cập nhật sản phẩm: " + updatedItem.getName());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeleteProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm trong bảng để xóa!");
            return;
        }

        // Cảnh báo xác nhận xóa rủi ro cao (Rất quan trọng trong UX)
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Xác nhận xóa");
        confirmDialog.setHeaderText("Xóa sản phẩm: " + selectedItem.getName());
        confirmDialog.setContentText("Bạn có chắc chắn muốn xóa? Thao tác này không thể hoàn tác.");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {

            // Tạo gói tin gửi server (Chờ ghép code)
            MessageDTO request = new MessageDTO("DELETE_ITEM", selectedItem.getItemId());
            System.out.println("Sẵn sàng gửi lệnh XÓA: " + request.getAction() + " | " + request.getPayload());

            // Xóa khỏi danh sách hiện tại
            itemList.remove(selectedItem);
        }
    }


    @FXML
    private void handleLogout() {
        try {
            // Tải lại file giao diện Đăng nhập
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/login.fxml"));
            Parent root = loader.load();



            Scene currentScene = tableItems.getScene();
            currentScene.setRoot(root);

            // Lấy Stage để set lại tiêu đề
            Stage stage = (Stage) currentScene.getWindow();
            stage.setTitle("Đăng nhập - AuctionVN");

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể tải màn hình đăng nhập!");
        }
    }


    // Hàm phụ trợ để hiện thông báo nhanh
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    @FXML
    void handleEditProduct(ActionEvent event) {
        // Lấy sản phẩm đang được chọn trong bảng
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Cảnh báo", "Vui lòng click chọn một sản phẩm trong bảng trước khi bấm Sửa!");
            return;
        }

        // Tạm thời báo thành công để test giao diện (Phần logic đổ dữ liệu vào form làm sau)
        showAlert("Thông báo", "Đã chọn sản phẩm: " + selectedItem.getName() + ". Sẵn sàng mở form sửa!");
    }
}