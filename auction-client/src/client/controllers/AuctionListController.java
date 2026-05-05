package client.controllers;

import client.models.AuctionViewModel;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.lang.reflect.Type;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class AuctionListController implements Initializable {

    @FXML private VBox auctionContainer;
    @FXML private ToggleButton btnTabLive, btnTabWon;

    private ToggleGroup tabGroup;
    private List<AuctionViewModel> allAuctions = new ArrayList<>(); // Lưu cache từ Server
    private String currentTab = "LIVE";

    private final Gson gson = new Gson();
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));
    private String myUsername;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.myUsername = client.models.UserSession.username;

        // Nhóm 2 nút Toggle lại thành 1 Tab menu
        tabGroup = new ToggleGroup();
        if(btnTabLive != null) btnTabLive.setToggleGroup(tabGroup);
        if(btnTabWon != null) btnTabWon.setToggleGroup(tabGroup);

        ClientMain.registerListener("AUCTION_LIST", payload -> {
            Type listType = new TypeToken<List<AuctionViewModel>>(){}.getType();
            allAuctions = gson.fromJson(payload, listType);
            Platform.runLater(this::applyFilterAndRender);
        });

        loadAuctionsFromServer();
    }

    @FXML
    void switchTab(ActionEvent event) {
        if (btnTabWon.isSelected()) currentTab = "WON";
        else currentTab = "LIVE";
        applyFilterAndRender();
    }

    private void applyFilterAndRender() {
        List<AuctionViewModel> filtered = new ArrayList<>();

        for (AuctionViewModel a : allAuctions) {
            if ("WON".equals(currentTab)) {
                // Chỉ lấy đồ đã KẾT THÚC và người thắng LÀ MÌNH
                if ("FINISHED".equalsIgnoreCase(a.getStatus()) && myUsername.equals(a.getCurrentWinner())) {
                    filtered.add(a);
                }
            } else {
                // Đang diễn ra hoặc sắp bắt đầu
                if (!"FINISHED".equalsIgnoreCase(a.getStatus()) && !"CANCELED".equalsIgnoreCase(a.getStatus())) {
                    filtered.add(a);
                }
            }
        }
        renderAuctionCards(filtered);
    }

    private void loadAuctionsFromServer() { /* Giữ nguyên của bạn */
        Platform.runLater(() -> {
            auctionContainer.getChildren().clear();
            auctionContainer.getChildren().add(new Label("Đang tải dữ liệu..."));
        });
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("GET_AVAILABLE_AUCTIONS", "")))).start();
    }

    private void renderAuctionCards(List<AuctionViewModel> list) { /* Giữ nguyên của bạn */
        auctionContainer.getChildren().clear();
        if (list == null || list.isEmpty()) {
            Label empty = new Label(currentTab.equals("WON") ? "Bạn chưa trúng đấu giá vật phẩm nào." : "Hiện không có phiên đấu giá nào đang mở.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
            auctionContainer.getChildren().add(empty);
            return;
        }
        for (AuctionViewModel auction : list) { auctionContainer.getChildren().add(buildCard(auction)); }
    }

    private HBox buildCard(AuctionViewModel auction) {
        // [Toàn bộ logic buildCard của bạn giữ nguyên, KHÔNG CẦN THAY ĐỔI]
        // Vì tiết kiệm ký tự, bạn cứ paste lại nguyên xi hàm buildCard của bạn ở đây nhé!
        Label icon = new Label(iconFor(auction.getStatus()));
        icon.setStyle("-fx-font-size: 40px; -fx-text-fill: #94a3b8;");
        Label lblName = new Label(auction.getItemName());
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Label lblBadge = new Label(badgeTextFor(auction.getStatus()));
        lblBadge.setStyle(badgeStyleFor(auction.getStatus()));
        HBox nameRow = new HBox(10, lblName, lblBadge); nameRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lblSub = new Label("ID: " + auction.getId()); lblSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        VBox colWinner = metaCol("👤 Người dẫn đầu", auction.getCurrentWinner());
        VBox colPrice = metaCol("💰 Giá hiện tại", VND.format((long) auction.getCurrentPrice()) + " đ");
        ((Label) colPrice.getChildren().get(1)).setStyle("-fx-font-weight: bold; -fx-text-fill: #ef4444;");
        HBox metaRow = new HBox(40, colWinner, colPrice); metaRow.setPadding(new Insets(10, 0, 0, 0));
        VBox info = new VBox(5, nameRow, lblSub, metaRow); HBox.setHgrow(info, Priority.ALWAYS);
        Button btnDetail = new Button("🔍 Chi tiết");
        btnDetail.setStyle("-fx-background-color: transparent; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");
        btnDetail.setPrefHeight(40); btnDetail.setUserData(String.valueOf(auction.getId()));
        btnDetail.setOnAction(this::viewDetail);
        HBox card = new HBox(20, icon, info, btnDetail); card.setAlignment(javafx.geometry.Pos.CENTER_LEFT); card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;");
        return card;
    }

    private VBox metaCol(String label, String value) { /* Giữ nguyên */
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Label val = new Label(value); val.setStyle("-fx-font-weight: bold;"); return new VBox(2, lbl, val);
    }
    private String iconFor(String status) { /* Giữ nguyên */ return status == null ? "📦" : switch (status.toUpperCase()) { case "RUNNING" -> "🔴"; case "FINISHED" -> "✅"; case "CANCELED" -> "❌"; default -> "🕐"; }; }
    private String badgeTextFor(String status) { /* Giữ nguyên */ return status == null ? "" : switch (status.toUpperCase()) { case "OPEN" -> "🕐 Sắp diễn ra"; case "RUNNING" -> "● Đang diễn ra"; case "FINISHED" -> "✓ Kết thúc"; case "CANCELED" -> "✗ Đã hủy"; default -> status; }; }
    private String badgeStyleFor(String status) { /* Giữ nguyên */ String base = "-fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;"; return status == null ? base : switch (status.toUpperCase()) { case "RUNNING" -> base + "-fx-background-color: #dcfce7; -fx-text-fill: #166534;"; case "FINISHED" -> base + "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;"; case "CANCELED" -> base + "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;"; default -> base + "-fx-background-color: #fef9c3; -fx-text-fill: #854d0e;"; }; }

    @FXML void viewDetail(ActionEvent event) { /* Giữ nguyên của bạn */
        Button btn = (Button) event.getSource(); String auctionId = btn.getUserData() != null ? btn.getUserData().toString() : btn.getId();
        try { ClientMain.unregisterListener("AUCTION_LIST"); FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/auction-detail.fxml")); Parent root = loader.load();
            AuctionDetailController dc = loader.getController(); dc.setRoomId(auctionId); Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow(); stage.getScene().setRoot(root); } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML void handleLogout(ActionEvent event) { /* Giữ nguyên của bạn */
        ClientMain.unregisterListener("AUCTION_LIST"); try { Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml")); Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow(); stage.getScene().setRoot(root); } catch (Exception e) { e.printStackTrace(); }
    }
}