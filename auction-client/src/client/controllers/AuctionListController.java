package client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.scene.control.TableView;
import client.models.AuctionViewModel;

public class AuctionListController {

    @FXML
    private TableView<AuctionViewModel> tableItems;

    // THÊM HÀM NÀY: Hàm initialize sẽ tự động chạy khi mở màn hình
    @FXML
    public void initialize() {
        // Mở một luồng chạy ngầm (Background Thread) để không đơ app
        new Thread(() -> {
            client.networks.MessageDTO req = new client.networks.MessageDTO("GET_AVAILABLE_AUCTIONS", "");
            client.networks.ClientMain.send(new Gson().toJson(req));

            try {
                String resJson = client.networks.ClientMain.receive();
                if (resJson != null) {
                    client.networks.MessageDTO res = new Gson().fromJson(resJson, client.networks.MessageDTO.class);

                    if ("AUCTION_LIST".equals(res.getAction())) {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.ArrayList<AuctionViewModel>>(){}.getType();
                        java.util.List<AuctionViewModel> serverList = new Gson().fromJson(res.getPayload(), listType);

                        // --- QUAN TRỌNG: Đẩy dữ liệu lên UI phải dùng Platform.runLater ---
                        Platform.runLater(() -> {
                            javafx.collections.ObservableList<AuctionViewModel> list = javafx.collections.FXCollections.observableArrayList(serverList);

                            tableItems.setItems(list);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start(); // Lệnh này kích hoạt luồng chạy ngầm
    }

    @FXML
    void viewDetail(ActionEvent event) {
        // 1. Lấy món hàng đang được chọn trên bảng
        AuctionViewModel selectedAuction = tableItems.getSelectionModel().getSelectedItem();

        if (selectedAuction == null) {
            System.out.println("Vui lòng chọn 1 món hàng trước!");
            return;
        }

        try {
            // 2. Load màn hình chi tiết
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/auction-detail.fxml"));
            Parent root = loader.load();

            // 3. Lấy Controller của màn hình chi tiết ra và truyền ID vào
            AuctionDetailController detailController = loader.getController();
            detailController.setRoomId(Long.valueOf(selectedAuction.getId()));

            // 4. Chuyển cảnh (Giữ nguyên form to)
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

            // Giữ nguyên khung màn hình to, chỉ thay ruột về trang Login
            stage.getScene().setRoot(root);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}