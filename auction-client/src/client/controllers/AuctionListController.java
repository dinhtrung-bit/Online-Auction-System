package client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import client.models.AuctionViewModel;
import client.networks.ClientMain;
import client.networks.MessageDTO;

public class AuctionListController {

    @FXML
    private VBox auctionContainer;

    @FXML
    public void initialize() {
        // Tự động tải danh sách khi vừa vào trang
        loadAuctionsFromServer();
    }

    private void loadAuctionsFromServer() {
        new Thread(() -> {
            try {
                MessageDTO req = new MessageDTO("GET_AVAILABLE_AUCTIONS", "");
                ClientMain.send(new Gson().toJson(req));

                String resJson = ClientMain.receive();
                if (resJson != null) {
                    MessageDTO res = new Gson().fromJson(resJson, MessageDTO.class);
                    if ("AUCTION_LIST".equals(res.getAction())) {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.ArrayList<AuctionViewModel>>(){}.getType();
                        java.util.List<AuctionViewModel> serverList = new Gson().fromJson(res.getPayload(), listType);

                        Platform.runLater(() -> {
                            // Xóa các card cũ và hiển thị card mới từ Server
                            renderAuctionCards(serverList);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void renderAuctionCards(java.util.List<AuctionViewModel> list) {
        // Logic này sẽ tạo ra các hàng đấu giá như trong ảnh của bạn
        // Lưu ý: Mỗi nút Chi tiết phải được gán UserData là AuctionId
    }

    @FXML
    void viewDetail(ActionEvent event) {
        // Lấy nút được nhấn từ sự kiện
        Button btn = (Button) event.getSource();

        // Lấy dữ liệu ID gắn trên nút (Ví dụ ID: 327060FF)
        Object data = btn.getUserData();
        String auctionId = (data != null) ? data.toString() : "";

        if (auctionId.isEmpty()) {
            // Nếu chưa có UserData, thử lấy ID từ thuộc tính ID của nút trong FXML
            auctionId = btn.getId();
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/auction-detail.fxml"));
            Parent root = loader.load();

            AuctionDetailController detailController = loader.getController();

            // Truyền ID vào màn hình chi tiết
            detailController.setRoomId(auctionId);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}