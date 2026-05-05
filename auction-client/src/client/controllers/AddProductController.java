package client.controllers;

import client.models.Art;
import client.models.Electronics;
import client.models.Item;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class AddProductController {

    @FXML private ComboBox<String> cmbCategory;
    @FXML private TextField txtId;
    @FXML private TextField txtName;
    @FXML private TextField txtPrice;

    // CÁC TRƯỜNG MỚI THÊM
    @FXML private TextField txtBidIncrement;
    @FXML private TextField txtDuration;
    @FXML private ImageView imgPreview;

    @FXML private Label lblDynamic;
    @FXML private TextField txtDynamic;

    private Item resultItem = null;
    private String selectedImagePath = ""; // Lưu đường dẫn ảnh
    private boolean isEditMode = false;    // Biến cờ: Nhận biết đang Thêm hay Sửa

    @FXML
    public void initialize() {
        cmbCategory.setItems(FXCollections.observableArrayList("Nghệ thuật (Art)", "Đồ điện tử (Electronics)"));
        cmbCategory.getSelectionModel().selectFirst();
        updateDynamicField("Nghệ thuật (Art)");

        cmbCategory.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateDynamicField(newVal);
        });
    }
    // Hàm này giúp thay đổi label tương ứng khi chọn Nghệ thuật hay Đồ điện tử
    private void updateDynamicField(String category) {
        if (category != null && category.contains("Art")) {
            lblDynamic.setText("Tên tác giả:");
            txtDynamic.setPromptText("Nhập tên tác giả...");
        } else {
            lblDynamic.setText("Bảo hành (tháng):");
            txtDynamic.setPromptText("Nhập số tháng bảo hành...");
        }
    }

    // TÍNH NĂNG ĂN ĐIỂM: Mở cửa sổ chọn file ảnh từ máy tính
    @FXML
    private void handleChooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh minh họa sản phẩm");
        // Chỉ cho phép chọn file ảnh
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        Stage stage = (Stage) txtId.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            selectedImagePath = file.getAbsolutePath();
            // Hiển thị ảnh xem trước lên ImageView
            Image image = new Image(file.toURI().toString());
            imgPreview.setImage(image);
        }
    }

    // TÍNH NĂNG ĂN ĐIỂM (DRY): Hàm này được gọi từ SellerDashboard nếu người dùng bấm "Sửa"
    public void setEditData(Item item) {
        isEditMode = true;
        txtId.setText(item.getItemId());
        txtId.setDisable(true); // Không cho phép sửa ID
        txtName.setText(item.getName());
        txtPrice.setText(String.valueOf(item.getStartingPrice()));
        txtBidIncrement.setText(String.valueOf(item.getBidIncrement()));
        txtDuration.setText(String.valueOf(item.getDurationMinutes()));

        // Load ảnh nếu có
        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            selectedImagePath = item.getImagePath();
            imgPreview.setImage(new Image(new File(selectedImagePath).toURI().toString()));
        }

        if (item instanceof Art) {
            cmbCategory.getSelectionModel().select("Nghệ thuật (Art)");
            // Giả sử có hàm lấy Artist
            // txtDynamic.setText(((Art) item).getArtist());
        } else if (item instanceof Electronics) {
            cmbCategory.getSelectionModel().select("Đồ điện tử (Electronics)");
            // txtDynamic.setText(String.valueOf(((Electronics) item).getWarrantyMonths()));
        }
        cmbCategory.setDisable(true); // Đã tạo rồi thì không cho đổi loại hình
    }

    @FXML
    private void handleSave() {
        try {
            String id = txtId.getText();
            String name = txtName.getText();
            double price = Double.parseDouble(txtPrice.getText());
            double bidInc = Double.parseDouble(txtBidIncrement.getText());
            int duration = Integer.parseInt(txtDuration.getText());
            String dynamicValue = txtDynamic.getText();

            String category = cmbCategory.getValue();
            if (category.contains("Art")) {
                resultItem = new Art(id, name, price, dynamicValue);
            } else {
                int warranty = Integer.parseInt(dynamicValue);
                resultItem = new Electronics(id, name, price, warranty);
            }

            // Gán các thông số mới vào
            resultItem.setBidIncrement(bidInc);
            resultItem.setDurationMinutes(duration);
            resultItem.setImagePath(selectedImagePath);

            closeWindow();
        } catch (NumberFormatException e) {
            showAlert("Lỗi nhập liệu", "Giá, Bước nhảy và Thời gian phải là số hợp lệ!");
        }
    }

    @FXML
    private void handleCancel() {
        resultItem = null;
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) txtId.getScene().getWindow();
        stage.close();
    }

    public Item getResultItem() { return resultItem; }
    public boolean isEditMode() { return isEditMode; }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}