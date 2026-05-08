package client.controllers;

import client.models.auction.AuctionViewModel;
import client.models.user.UserSession;
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
    @FXML private ComboBox<String> cmbStatus; // ✅ THÊM: ComboBox chọn trạng thái
    @FXML private Label lblBalance;

    private ToggleGroup tabGroup;
    private List<AuctionViewModel> allAuctions = new ArrayList<>(); // Lưu cache từ Server
    private String currentTab = "LIVE";
    private String currentStatusFilter = "ALL"; // ✅ THÊM: Lưu trạng thái lọc hiện tại

    private final Gson gson = new Gson();
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));
    private String myUsername;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.myUsername = UserSession.username;

        // Nhóm 2 nút Toggle lại thành 1 Tab menu
        tabGroup = new ToggleGroup();
        if(btnTabLive != null) btnTabLive.setToggleGroup(tabGroup);
        if(btnTabWon != null) btnTabWon.setToggleGroup(tabGroup);

        // ✅ THÊM: Setup ComboBox chọn trạng thái
        if (cmbStatus != null) {
            cmbStatus.setValue("TẤT CẢ");
        }

        // Đăng ký nhận danh sách đấu giá
        ClientMain.registerListener("AUCTION_LIST", payload -> {
            Type listType = new TypeToken<List<AuctionViewModel>>(){}.getType();
            allAuctions = gson.fromJson(payload, listType);
            Platform.runLater(this::applyFilterAndRender);
        });

        // ✅ THÊM: Đăng ký lắng nghe danh sách lọc theo trạng thái từ Server
        ClientMain.registerListener("AUCTION_LIST_BY_STATUS", payload -> {
            Type listType = new TypeToken<List<AuctionViewModel>>(){}.getType();
            List<AuctionViewModel> filteredAuctions = gson.fromJson(payload, listType);
            Platform.runLater(() -> {
                applyTabFilterAndRender(filteredAuctions);
            });
        });

        // THÊM: Đăng ký lắng nghe tin nhắn chứa Số dư (BALANCE_DATA) từ Server
        ClientMain.registerListener("BALANCE_DATA", payload -> {
            Platform.runLater(() -> {
                try {
                    double balanceVal = Double.parseDouble(payload);
                    // Lưu vào Session để màn hình Đặt giá (AuctionDetail) có thể dùng để kiểm tra
                    UserSession.balance = balanceVal;
                    // Hiển thị lên giao diện
                    if (lblBalance != null) {
                        lblBalance.setText("💳 Số dư: " + VND.format(balanceVal) + " đ");
                    }
                } catch (Exception e) {
                    System.out.println("Lỗi hiển thị số dư: " + e.getMessage());
                }
            });
        });

        // Đăng ký lắng nghe kho vật phẩm đã thắng từ Server
        ClientMain.registerListener("WON_AUCTIONS", payload -> {
            try {
                java.lang.reflect.Type listType =
                        new com.google.gson.reflect.TypeToken<
                                java.util.List<java.util.Map<String, Object>>>(){}.getType();
                java.util.List<java.util.Map<String, Object>> wonList =
                        gson.fromJson(payload, listType);
                Platform.runLater(() -> renderWonAuctionCards(wonList));
            } catch (Exception e) {
                System.err.println("Lỗi parse WON_AUCTIONS: " + e.getMessage());
            }
        });

        // Tải danh sách đấu giá
        loadAuctionsFromServer();

        new Thread(() -> {
            ClientMain.send(gson.toJson(new MessageDTO("GET_BALANCE", "")));
        }).start();
    }

    @FXML
    void switchTab(ActionEvent event) {
        if (btnTabWon != null && btnTabWon.isSelected()) {
            currentTab = "WON";
            // Gửi request lấy kho vật phẩm đã thắng từ Server
            new Thread(() -> ClientMain.send(
                    gson.toJson(new MessageDTO("GET_MY_WON_AUCTIONS", "")))).start();
        } else {
            currentTab = "LIVE";
            applyFilterAndRender();
        }
    }

    // ✅ THÊM: Handler cho ComboBox chọn trạng thái
    @FXML
    void handleFilterByStatus(ActionEvent event) {
        String selectedStatus = cmbStatus.getValue();

        if (selectedStatus == null || selectedStatus.equals("TẤT CẢ")) {
            // Hiển thị tất cả
            currentStatusFilter = "ALL";
            applyFilterAndRender();
        } else {
            // Gửi yêu cầu lọc đến Server
            currentStatusFilter = selectedStatus;
            new Thread(() -> {
                ClientMain.send(gson.toJson(new MessageDTO("GET_AUCTIONS_BY_STATUS", selectedStatus)));
            }).start();
        }
    }

    private void applyFilterAndRender() {
        // Nếu có lọc theo trạng thái, bỏ qua lọc này
        if (!currentStatusFilter.equals("ALL")) {
            return; // Để applyTabFilterAndRender xử lý
        }

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

    // ✅ THÊM: Method áp dụng lọc Tab cho danh sách đã lọc theo trạng thái
    private void applyTabFilterAndRender(List<AuctionViewModel> statusFilteredList) {
        List<AuctionViewModel> filtered = new ArrayList<>();

        for (AuctionViewModel a : statusFilteredList) {
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

    private void loadAuctionsFromServer() {
        Platform.runLater(() -> {
            auctionContainer.getChildren().clear();
            auctionContainer.getChildren().add(new Label("Đang tải dữ liệu..."));
        });
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("GET_AVAILABLE_AUCTIONS", "")))).start();
    }

    private void renderAuctionCards(List<AuctionViewModel> list) {
        auctionContainer.getChildren().clear();
        if (list == null || list.isEmpty()) {
            Label empty = new Label(currentTab.equals("WON")
                    ? "Bạn chưa trúng đấu giá vật phẩm nào."
                    : "Hiện không có phiên đấu giá nào đang mở.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
            auctionContainer.getChildren().add(empty);
            return;
        }
        for (AuctionViewModel auction : list) {
            auctionContainer.getChildren().add(buildCard(auction));
        }
    }

    /** Render kho vật phẩm đã trúng đấu giá — dữ liệu thật từ Server */
    private void renderWonAuctionCards(java.util.List<java.util.Map<String, Object>> wonList) {
        auctionContainer.getChildren().clear();

        if (wonList == null || wonList.isEmpty()) {
            Label empty = new Label("🎁 Kho vật phẩm trống. Hãy tham gia đấu giá và giành chiến thắng!");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-style: italic;");
            auctionContainer.getChildren().add(empty);
            return;
        }

        // Tiêu đề kho
        Label title = new Label("🏆 Kho vật phẩm đã trúng đấu giá (" + wonList.size() + " vật phẩm)");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0c4a6e;");
        auctionContainer.getChildren().add(title);

        for (java.util.Map<String, Object> item : wonList) {
            auctionContainer.getChildren().add(buildWonCard(item));
        }
    }

    private HBox buildWonCard(java.util.Map<String, Object> item) {
        // Icon
        Label icon = new Label("🏅");
        icon.setStyle("-fx-font-size: 36px;");

        // Tên vật phẩm
        Label lblName = new Label(String.valueOf(item.get("itemName")));
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        // Badge trạng thái
        String status = String.valueOf(item.getOrDefault("status", "PAID"));
        Label lblBadge = new Label("PAID".equals(status) ? "💰 Đã thanh toán" : "✅ Đã kết thúc");
        lblBadge.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1e3a8a; "
                + "-fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");

        HBox nameRow = new HBox(10, lblName, lblBadge);
        nameRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Giá cuối
        double finalPrice = ((Number) item.getOrDefault("finalPrice", 0)).doubleValue();
        VBox colPrice = metaCol("💰 Giá trúng",
                VND.format((long) finalPrice) + " đ");
        ((Label) colPrice.getChildren().get(1))
                .setStyle("-fx-font-weight: bold; -fx-text-fill: #059669;");

        // Thời gian kết thúc
        String endTime = String.valueOf(item.getOrDefault("endTime", ""));
        String endTimeShort = endTime.length() >= 16 ? endTime.substring(0, 16).replace("T", " ") : endTime;
        VBox colTime = metaCol("🕐 Kết thúc lúc", endTimeShort);

        // ID phiên
        VBox colId = metaCol("🔖 Phiên #",
                String.valueOf(item.getOrDefault("auctionId", "")));

        HBox metaRow = new HBox(40, colPrice, colTime, colId);
        metaRow.setPadding(new Insets(8, 0, 0, 0));

        VBox info = new VBox(5, nameRow, metaRow);
        HBox.setHgrow(info, Priority.ALWAYS);

        // Card container
        HBox card = new HBox(16, icon, info);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 10; "
                + "-fx-border-color: #86efac; -fx-border-radius: 10; -fx-border-width: 1.5;");
        return card;
    }

    private HBox buildCard(AuctionViewModel auction) {
        Label icon = new Label(iconFor(auction.getStatus()));
        icon.setStyle("-fx-font-size: 40px; -fx-text-fill: #94a3b8;");
        Label lblName = new Label(auction.getItemName());
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Label lblBadge = new Label(badgeTextFor(auction.getStatus()));
        lblBadge.setStyle(badgeStyleFor(auction.getStatus()));
        HBox nameRow = new HBox(10, lblName, lblBadge);
        nameRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lblSub = new Label("ID: " + auction.getId());
        lblSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        VBox colWinner = metaCol("👤 Người dẫn đầu", auction.getCurrentWinner());
        VBox colPrice = metaCol("💰 Giá hiện tại", VND.format((long) auction.getCurrentPrice()) + " đ");
        ((Label) colPrice.getChildren().get(1)).setStyle("-fx-font-weight: bold; -fx-text-fill: #ef4444;");
        HBox metaRow = new HBox(40, colWinner, colPrice);
        metaRow.setPadding(new Insets(10, 0, 0, 0));
        VBox info = new VBox(5, nameRow, lblSub, metaRow);
        HBox.setHgrow(info, Priority.ALWAYS);
        Button btnDetail = new Button("🔍 Chi tiết");
        btnDetail.setStyle("-fx-background-color: transparent; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");
        btnDetail.setPrefHeight(40);
        btnDetail.setUserData(String.valueOf(auction.getId()));
        btnDetail.setOnAction(this::viewDetail);
        HBox card = new HBox(20, icon, info, btnDetail);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;");
        return card;
    }

    private VBox metaCol(String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Label val = new Label(value);
        val.setStyle("-fx-font-weight: bold;");
        return new VBox(2, lbl, val);
    }

    private String iconFor(String status) {
        return status == null ? "📦" : switch (status.toUpperCase()) {
            case "RUNNING" -> "🔴";
            case "FINISHED" -> "✅";
            case "CANCELED" -> "❌";
            default -> "🕐";
        };
    }

    private String badgeTextFor(String status) {
        return status == null ? "" : switch (status.toUpperCase()) {
            case "OPEN" -> "🕐 Sắp diễn ra";
            case "RUNNING" -> "● Đang diễn ra";
            case "FINISHED" -> "✓ Kết thúc";
            case "CANCELED" -> "✗ Đã hủy";
            case "PAID" -> "💳 Đã thanh toán";
            default -> status;
        };
    }

    private String badgeStyleFor(String status) {
        String base = "-fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;";
        return status == null ? base : switch (status.toUpperCase()) {
            case "RUNNING" -> base + " -fx-background-color: #dcfce7; -fx-text-fill: #166534;";
            case "FINISHED" -> base + " -fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";
            case "CANCELED" -> base + " -fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
            case "PAID" -> base + " -fx-background-color: #dbeafe; -fx-text-fill: #0c4a6e;";
            default -> base + " -fx-background-color: #fef9c3; -fx-text-fill: #854d0e;";
        };
    }

    @FXML
    void viewDetail(ActionEvent event) {
        Button btn = (Button) event.getSource();
        String auctionId = btn.getUserData() != null ? btn.getUserData().toString() : btn.getId();
        try {
            ClientMain.unregisterListener("AUCTION_LIST");
            ClientMain.unregisterListener("BALANCE_DATA");
            ClientMain.unregisterListener("AUCTION_LIST_BY_STATUS");
            ClientMain.unregisterListener("WON_AUCTIONS");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/auction-detail.fxml"));
            Parent root = loader.load();
            AuctionDetailController dc = loader.getController();
            dc.setRoomId(auctionId);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    void handleDeposit(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nạp tiền");
        dialog.setHeaderText("Nhập số tiền muốn nạp vào ví");
        dialog.setContentText("Số tiền (VNĐ):");

        dialog.showAndWait().ifPresent(input -> {
            try {
                double amount = Double.parseDouble(input.replaceAll("[^\\d]", ""));
                if (amount <= 0) {
                    showSimpleAlert("Số tiền phải lớn hơn 0!");
                    return;
                }

                ClientMain.registerListener("DEPOSIT_SUCCESS", payload -> {
                    ClientMain.unregisterListener("DEPOSIT_SUCCESS");
                    ClientMain.unregisterListener("DEPOSIT_FAILED");
                    Platform.runLater(() -> {
                        UserSession.balance += amount;
                        if (lblBalance != null) {
                            lblBalance.setText("💳 Số dư: " + VND.format((long) UserSession.balance) + " đ");
                        }
                        showSimpleAlert("Nạp tiền thành công! Số dư mới: " + VND.format((long) UserSession.balance) + " đ");
                    });
                });

                ClientMain.registerListener("DEPOSIT_FAILED", payload -> {
                    ClientMain.unregisterListener("DEPOSIT_SUCCESS");
                    ClientMain.unregisterListener("DEPOSIT_FAILED");
                    Platform.runLater(() -> showSimpleAlert("Nạp tiền thất bại: " + payload));
                });

                new Thread(() -> ClientMain.send(gson.toJson(
                        new MessageDTO("DEPOSIT", String.valueOf(amount))
                ))).start();

            } catch (NumberFormatException e) {
                showSimpleAlert("Số tiền không hợp lệ!");
            }
        });
    }

    private void showSimpleAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    @FXML
    void handleLogout(ActionEvent event) {
        ClientMain.unregisterListener("AUCTION_LIST");
        ClientMain.unregisterListener("BALANCE_DATA");
        ClientMain.unregisterListener("AUCTION_LIST_BY_STATUS");
        ClientMain.unregisterListener("WON_AUCTIONS");
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}