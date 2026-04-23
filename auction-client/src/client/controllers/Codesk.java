package client.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class Codesk {

    // Khai báo các thành phần UI (Id phải khớp với trong Scene Builder)
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private Label lblSystemMessage;

    private IBidService bidService;
    private int currentUserId = 1; // Giả sử user có ID = 1 đang đăng nhập
    private int currentRoomId = 101;

    // Hàm này chạy ngay khi màn hình vừa mở lên
    @FXML
    public void initialize() {
        // Tạm thời khởi tạo giả lập, sau này bạn sẽ truyền Socket thật vào đây
        // this.bidService = new BidServiceImpl(socket, outStream);
    }

    // Sự kiện khi bấm nút "Đặt giá"
    @FXML
    public void handlePlaceBidAction() {
        try {
            double amount = Double.parseDouble(txtBidAmount.getText());

            // Gọi tầng Service để gửi lệnh qua mạng
            if (bidService != null) {
                bidService.processBid(currentUserId, currentRoomId, amount);
                lblSystemMessage.setText("Đang chờ Server phản hồi...");
                lblSystemMessage.setStyle("-fx-text-fill: blue;");
            }
        } catch (NumberFormatException e) {
            lblSystemMessage.setText("Lỗi: Vui lòng nhập số tiền hợp lệ!");
            lblSystemMessage.setStyle("-fx-text-fill: red;");
        } catch (Exception e) {
            lblSystemMessage.setText("Lỗi kết nối: " + e.getMessage());
        }
    }
}