package client.controllers;

import client.models.item.Art;
import client.models.item.Electronics;
import client.models.item.Item;
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
    @FXML private TextField txtBidIncrement;
    @FXML private TextArea txtDescription;
    @FXML private ImageView imgPreview;

    private Item resultItem = null;
    private String selectedImagePath = "";
    private boolean isEditMode = false;

    @FXML
    public void initialize() {
        cmbCategory.setItems(FXCollections.observableArrayList(
                "Nghệ thuật (Art)",
                "Đồ điện tử (Electronics)"
        ));
        cmbCategory.getSelectionModel().selectFirst();
        setupMoneyField(txtPrice);
        setupMoneyField(txtBidIncrement);
    }

    @FXML
    private void handleChooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh minh họa sản phẩm");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        Stage stage = (Stage) txtId.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            selectedImagePath = file.getAbsolutePath();
            imgPreview.setImage(new Image(file.toURI().toString()));
        }
    }

    public void setEditData(Item item) {
        if (item == null) return;

        isEditMode = true;

        txtId.setText(item.getItemId());
        txtId.setDisable(true);
        txtName.setText(item.getName());
        txtPrice.setText(String.format("%.0f", item.getStartingPrice()));
        txtBidIncrement.setText(String.format("%.0f", item.getBidIncrement()));
        txtDescription.setText(item.getDescription());

        String cat = item.getCategory();
        if (cat != null && cat.toUpperCase().contains("ELECT")) {
            cmbCategory.getSelectionModel().select("Đồ điện tử (Electronics)");
        } else if (item instanceof Electronics) {
            cmbCategory.getSelectionModel().select("Đồ điện tử (Electronics)");
        } else {
            cmbCategory.getSelectionModel().select("Nghệ thuật (Art)");
        }

        cmbCategory.setDisable(true);

        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            selectedImagePath = item.getImagePath();
            imgPreview.setImage(new Image(new File(selectedImagePath).toURI().toString()));
        }
    }

    @FXML
    private void handleSave() {
        try {
            String id = txtId.getText().trim();
            String name = txtName.getText().trim();
            String description = txtDescription.getText();

            if (id.isEmpty() || name.isEmpty()) {
                showAlert("Thiếu thông tin", "Vui lòng nhập mã sản phẩm và tên sản phẩm!");
                return;
            }

            if (txtPrice.getText().trim().isEmpty() || txtBidIncrement.getText().trim().isEmpty()) {
                showAlert("Thiếu thông tin", "Vui lòng nhập giá khởi điểm và bước nhảy!");
                return;
            }

            double price = Double.parseDouble(txtPrice.getText().trim());
            double bidInc = Double.parseDouble(txtBidIncrement.getText().trim());

            if (price <= 0 || bidInc <= 0) {
                showAlert("Lỗi nhập liệu", "Giá khởi điểm và bước nhảy phải lớn hơn 0!");
                return;
            }

            if (bidInc > price) {
                showAlert("Bước giá chưa hợp lý", "Bước nhảy đang lớn hơn giá khởi điểm. Hãy chọn bước giá nhỏ hơn để người mua dễ tham gia hơn.");
                return;
            }

            String category = cmbCategory.getValue();

            if (category.contains("Art")) {
                resultItem = new Art(id, name, price, "");
                resultItem.setCategory("ART");
            } else {
                resultItem = new Electronics(id, name, price, 0);
                resultItem.setCategory("ELECTRONICS");
            }

            resultItem.setBidIncrement(bidInc);
            resultItem.setImagePath(selectedImagePath);
            resultItem.setDescription(description);

            closeWindow();

        } catch (NumberFormatException e) {
            showAlert("Lỗi nhập liệu", "Giá khởi điểm và bước nhảy phải là số hợp lệ!");
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

    public Item getResultItem() {
        return resultItem;
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    private void setupMoneyField(TextField field) {
        if (field == null) return;
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            String sanitized = newVal.replaceAll("[^\\d]", "");
            if (!newVal.equals(sanitized)) field.setText(sanitized);
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (content != null && content.length() > 180) {
            TextArea area = new TextArea(content);
            area.setWrapText(true);
            area.setEditable(false);
            area.setPrefWidth(500);
            area.setPrefHeight(220);
            alert.getDialogPane().setContent(area);
        } else {
            alert.setContentText(content);
        }
        alert.showAndWait();
    }
}