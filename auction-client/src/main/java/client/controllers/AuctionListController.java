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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.lang.reflect.Type;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;

public class AuctionListController implements Initializable {

    @FXML private VBox auctionContainer;
    @FXML private ToggleButton btnTabLive, btnTabWon;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private ComboBox<String> cmbSort;
    @FXML private TextField txtAuctionSearch;
    @FXML private Label lblBalance;
    @FXML private Label lblUserName;
    @FXML private Label lblLiveCount;
    @FXML private Label lblWonCount;
    @FXML private Label lblBalanceCard;
    @FXML private Button btnNavHome, btnNavLive, btnNavWon, btnNavWallet, btnNavSettings;
    @FXML private ScrollPane scrollAuctions;

    private ToggleGroup tabGroup;
    private List<AuctionViewModel> allAuctions = new ArrayList<>();
    private List<AuctionViewModel> wonAuctions = new ArrayList<>();
    private String currentTab = "LIVE";
    private String currentStatusFilter = "TẤT CẢ";

    private final Gson gson = new Gson();
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));
    private String myUsername;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.myUsername = UserSession.getInstance().getUsername();
        if (lblUserName != null) {
            lblUserName.setText(myUsername == null || myUsername.isBlank() ? "Người đấu giá" : myUsername);
        }

        setupTabsAndFilters();
        registerServerListeners();
        loadAuctionsFromServer();
        refreshBalance();
    }

    private void setupTabsAndFilters() {
        tabGroup = new ToggleGroup();
        if (btnTabLive != null) btnTabLive.setToggleGroup(tabGroup);
        if (btnTabWon != null) btnTabWon.setToggleGroup(tabGroup);

        if (cmbStatus != null) {
            cmbStatus.getItems().setAll("TẤT CẢ", "OPEN", "RUNNING");
            cmbStatus.setValue("TẤT CẢ");
        }

        if (cmbSort != null) {
            cmbSort.getItems().setAll("Mới nhất", "Giá tăng dần", "Giá giảm dần", "Tên A-Z");
            cmbSort.setValue("Mới nhất");
        }
    }

    private void registerServerListeners() {
        ClientMain.registerListener("AUCTION_LIST", payload -> {
            try {
                Type listType = new TypeToken<List<AuctionViewModel>>() {}.getType();
                List<AuctionViewModel> data = gson.fromJson(payload, listType);
                allAuctions = data == null ? new ArrayList<>() : data;
                Platform.runLater(() -> {
                    updateDashboardStats();
                    applyFilterAndRender();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Không đọc được danh sách phiên đấu giá."));
            }
        });

        ClientMain.registerListener("WON_AUCTIONS", payload -> {
            try {
                Type mapListType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> raw = gson.fromJson(payload, mapListType);
                List<AuctionViewModel> wonList = new ArrayList<>();
                if (raw != null) {
                    for (Map<String, Object> r : raw) {
                        int id = readInt(r.get("auctionId"));
                        String itemName = String.valueOf(r.getOrDefault("itemName", ""));
                        double price = readDouble(r.get("finalPrice"));
                        String status = String.valueOf(r.getOrDefault("status", "FINISHED"));
                        wonList.add(new AuctionViewModel(id, itemName, price, myUsername, status));
                    }
                }
                wonAuctions = wonList;
                Platform.runLater(() -> {
                    updateDashboardStats();
                    if ("WON".equals(currentTab)) applyFilterAndRender();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Không đọc được danh sách vật phẩm đã thắng."));
            }
        });

        ClientMain.registerListener("AUCTION_STARTED", payload -> Platform.runLater(this::loadAuctionsFromServer));
        ClientMain.registerListener("AUCTION_CANCELED", payload -> Platform.runLater(this::loadAuctionsFromServer));
        ClientMain.registerListener("AUCTION_FINISHED", payload -> Platform.runLater(() -> {
            loadAuctionsFromServer();
            loadWonAuctionsFromServer();
        }));

        ClientMain.registerListener("BALANCE_DATA", payload -> Platform.runLater(() -> {
            try {
                double balanceVal = Double.parseDouble(payload.trim());
                UserSession.getInstance().setBalance(balanceVal);
                updateBalanceLabels(balanceVal);
            } catch (Exception e) {
                System.out.println("Lỗi hiển thị số dư: " + e.getMessage());
            }
        }));

        ClientMain.registerListener("ERROR", payload -> Platform.runLater(() -> showError(payload)));
    }

    @FXML
    void switchTab(ActionEvent event) {
        if (btnTabWon != null && btnTabWon.isSelected()) {
            showWonTab();
        } else {
            showLiveTab();
        }
    }

    @FXML
    void handleNavHome(ActionEvent event) {
        resetFilters();
        showLiveTab();
        setActiveNav(btnNavHome);
    }

    @FXML
    void handleNavLive(ActionEvent event) {
        showLiveTab();
        setActiveNav(btnNavLive);
    }

    @FXML
    void handleNavWon(ActionEvent event) {
        showWonTab();
        setActiveNav(btnNavWon);
    }

    @FXML
    void handleNavWallet(ActionEvent event) {
        setActiveNav(btnNavWallet);
        handleDeposit(event);
        setActiveNav(btnNavHome);
    }

    @FXML
    void handleNavSettings(ActionEvent event) {
        setActiveNav(btnNavSettings);
        showSettingsDialog();
        setActiveNav("WON".equals(currentTab) ? btnNavWon : btnNavHome);
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadAuctionsFromServer();
        if ("WON".equals(currentTab)) loadWonAuctionsFromServer();
        refreshBalance();
    }

    @FXML
    void handleSearchAuctions() {
        applyFilterAndRender();
    }

    @FXML
    void handleSortAuctions(ActionEvent event) {
        applyFilterAndRender();
    }

    @FXML
    void handleFilterByStatus(ActionEvent event) {
        currentStatusFilter = cmbStatus == null || cmbStatus.getValue() == null
                ? "TẤT CẢ"
                : cmbStatus.getValue();
        applyFilterAndRender();
    }

    private void showLiveTab() {
        currentTab = "LIVE";
        if (btnTabLive != null) btnTabLive.setSelected(true);
        if (btnTabWon != null) btnTabWon.setSelected(false);
        if (cmbStatus != null) cmbStatus.setDisable(false);
        applyFilterAndRender();
        setActiveNav(btnNavLive != null && btnNavLive.isFocused() ? btnNavLive : btnNavHome);
    }

    private void showWonTab() {
        currentTab = "WON";
        if (btnTabWon != null) btnTabWon.setSelected(true);
        if (btnTabLive != null) btnTabLive.setSelected(false);
        if (cmbStatus != null) cmbStatus.setDisable(true);
        renderLoading("Đang tải vật phẩm đã thắng...");
        loadWonAuctionsFromServer();
        setActiveNav(btnNavWon);
    }

    private void resetFilters() {
        if (txtAuctionSearch != null) txtAuctionSearch.clear();
        if (cmbSort != null) cmbSort.setValue("Mới nhất");
        if (cmbStatus != null) cmbStatus.setValue("TẤT CẢ");
        currentStatusFilter = "TẤT CẢ";
    }

    private void applyFilterAndRender() {
        List<AuctionViewModel> source = "WON".equals(currentTab)
                ? new ArrayList<>(wonAuctions)
                : getLiveAuctions();

        String keyword = txtAuctionSearch == null ? "" : txtAuctionSearch.getText().trim().toLowerCase();
        if (!keyword.isEmpty()) {
            source.removeIf(a -> !safe(a.getItemName()).toLowerCase().contains(keyword)
                    && !String.valueOf(a.getId()).contains(keyword)
                    && !safe(a.getCurrentWinner()).toLowerCase().contains(keyword));
        }

        if (!"WON".equals(currentTab) && currentStatusFilter != null && !"TẤT CẢ".equals(currentStatusFilter)) {
            source.removeIf(a -> !currentStatusFilter.equalsIgnoreCase(safe(a.getStatus())));
        }

        sortAuctions(source);
        renderAuctionCards(source);
    }

    private List<AuctionViewModel> getLiveAuctions() {
        List<AuctionViewModel> filtered = new ArrayList<>();
        for (AuctionViewModel a : allAuctions) {
            if (!"FINISHED".equalsIgnoreCase(safe(a.getStatus()))
                    && !"CANCELED".equalsIgnoreCase(safe(a.getStatus()))
                    && !"PAID".equalsIgnoreCase(safe(a.getStatus()))) {
                filtered.add(a);
            }
        }
        return filtered;
    }

    private void sortAuctions(List<AuctionViewModel> source) {
        String sort = cmbSort == null || cmbSort.getValue() == null ? "Mới nhất" : cmbSort.getValue();
        switch (sort) {
            case "Giá tăng dần" -> source.sort(Comparator.comparingDouble(AuctionViewModel::getCurrentPrice));
            case "Giá giảm dần" -> source.sort(Comparator.comparingDouble(AuctionViewModel::getCurrentPrice).reversed());
            case "Tên A-Z" -> source.sort(Comparator.comparing(a -> safe(a.getItemName()).toLowerCase()));
            default -> source.sort(Comparator.comparingInt(AuctionViewModel::getId).reversed());
        }
    }

    private void loadAuctionsFromServer() {
        renderLoading("Đang tải dữ liệu phiên đấu giá...");
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("GET_AVAILABLE_AUCTIONS", ""))), "load-auctions").start();
    }

    private void loadWonAuctionsFromServer() {
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("GET_MY_WON_AUCTIONS", ""))), "load-won-auctions").start();
    }

    private void refreshBalance() {
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("GET_BALANCE", ""))), "get-balance").start();
    }

    private void renderLoading(String message) {
        if (auctionContainer == null) return;
        Platform.runLater(() -> {
            auctionContainer.getChildren().clear();
            VBox loading = new VBox(10);
            loading.setAlignment(Pos.CENTER);
            loading.setPadding(new Insets(42));
            loading.getStyleClass().add("empty-state");
            Label icon = new Label("⏳");
            icon.setStyle("-fx-font-size: 34px;");
            Label text = new Label(message);
            text.getStyleClass().add("empty-title");
            loading.getChildren().addAll(icon, text);
            auctionContainer.getChildren().add(loading);
        });
    }

    private void renderAuctionCards(List<AuctionViewModel> list) {
        auctionContainer.getChildren().clear();
        if (scrollAuctions != null) scrollAuctions.setVvalue(0);

        if (list == null || list.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(52));
            empty.getStyleClass().add("empty-state");

            Label icon = new Label("WON".equals(currentTab) ? "🏆" : "📭");
            icon.setStyle("-fx-font-size: 42px;");
            Label title = new Label("WON".equals(currentTab)
                    ? "Bạn chưa trúng vật phẩm nào"
                    : "Hiện không có phiên đấu giá phù hợp");
            title.getStyleClass().add("empty-title");
            Label sub = new Label("Thử đổi bộ lọc hoặc nhấn Làm mới để cập nhật dữ liệu.");
            sub.getStyleClass().add("empty-subtitle");
            empty.getChildren().addAll(icon, title, sub);
            auctionContainer.getChildren().add(empty);
            return;
        }

        for (AuctionViewModel auction : list) {
            auctionContainer.getChildren().add(buildCard(auction));
        }
    }

    private HBox buildCard(AuctionViewModel auction) {
        VBox imageBox = new VBox();
        imageBox.setPrefSize(92, 92);
        imageBox.setMinSize(92, 92);
        imageBox.setMaxSize(92, 92);
        imageBox.setAlignment(Pos.CENTER);
        imageBox.getStyleClass().add("auction-icon-box");

        String iconText = switch (safe(auction.getStatus()).toUpperCase()) {
            case "RUNNING" -> "🔥";
            case "OPEN" -> "⏳";
            case "FINISHED", "PAID" -> "🏆";
            default -> "📦";
        };
        Label icon = new Label(iconText);
        icon.setStyle("-fx-font-size: 34px;");
        imageBox.getChildren().add(icon);

        Label lblName = new Label(safe(auction.getItemName()).isBlank() ? "Sản phẩm chưa đặt tên" : auction.getItemName());
        lblName.getStyleClass().add("auction-card-title");
        lblName.setWrapText(true);

        Label lblId = new Label("Mã phiên #" + auction.getId());
        lblId.getStyleClass().add("auction-card-id");

        Label lblStatus = new Label(badgeTextFor(auction.getStatus()));
        lblStatus.setStyle(badgeStyleFor(auction.getStatus()));

        VBox titleBox = new VBox(7, lblName, lblId, lblStatus);

        VBox colPrice = metricBox("💰 Giá hiện tại", VND.format((long) auction.getCurrentPrice()) + " đ", "metric-price");
        VBox colWinner = metricBox("👤 Người dẫn đầu",
                safe(auction.getCurrentWinner()).isBlank() ? "Chưa có" : auction.getCurrentWinner(),
                "metric-normal");

        HBox infoBottom = new HBox(38, colPrice, colWinner);
        VBox infoBox = new VBox(14, titleBox, infoBottom);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Button btnDetail = new Button("🔍 Xem chi tiết");
        btnDetail.getStyleClass().add("auction-detail-button");
        btnDetail.setUserData(String.valueOf(auction.getId()));
        btnDetail.setOnAction(this::viewDetail);

        VBox actionBox = new VBox(8, btnDetail);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        HBox card = new HBox(24, imageBox, infoBox, actionBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("auction-card");
        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                btnDetail.fire();
            }
        });

        return card;
    }

    private VBox metricBox(String title, String value, String valueClass) {
        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("metric-label");
        Label lblValue = new Label(value);
        lblValue.getStyleClass().add(valueClass);
        lblValue.setWrapText(true);
        VBox box = new VBox(5, lblTitle, lblValue);
        box.setMinWidth(150);
        return box;
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
        String base = "-fx-padding: 5 10; -fx-background-radius: 999; -fx-font-size: 11px; -fx-font-weight: 900;";
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
        btn.setDisable(true);

        try {
            cleanupListeners();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/auction-detail.fxml"));
            Parent root = loader.load();
            AuctionDetailController dc = loader.getController();
            dc.setRoomId(auctionId);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            btn.setDisable(false);
            e.printStackTrace();
            showError("Không mở được chi tiết phiên đấu giá.");
        }
    }

    @FXML
    void handleDeposit(ActionEvent event) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Yêu cầu nạp tiền");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("modern-dialog-pane");

        ButtonType submitBtnType = new ButtonType("📨 Gửi yêu cầu", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(submitBtnType, ButtonType.CANCEL);

        VBox root = new VBox(18);
        root.getStyleClass().add("dialog-content");

        Label title = new Label("💰 Gửi yêu cầu nạp tiền");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label("Tiền sẽ vào ví sau khi Admin kiểm tra và duyệt yêu cầu.");
        subtitle.getStyleClass().add("dialog-subtitle");

        TextField txtAmount = new TextField();
        txtAmount.setPromptText("Ví dụ: 1000000");
        txtAmount.setPrefHeight(48);
        txtAmount.getStyleClass().add("input-field");

        TextArea txtNote = new TextArea();
        txtNote.setPromptText("Ghi chú chuyển khoản / mã giao dịch / nội dung thanh toán...");
        txtNote.setPrefRowCount(3);
        txtNote.setWrapText(true);
        txtNote.getStyleClass().add("text-area-clean");

        Label lblError = new Label();
        lblError.getStyleClass().add("error-text");

        HBox quickAmounts = new HBox(8);
        quickAmounts.getChildren().addAll(
                quickAmountButton(txtAmount, 500_000),
                quickAmountButton(txtAmount, 1_000_000),
                quickAmountButton(txtAmount, 5_000_000),
                quickAmountButton(txtAmount, 10_000_000)
        );

        txtAmount.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                txtAmount.setText(newVal.replaceAll("[^\\d]", ""));
                lblError.setText("❌ Chỉ được nhập số!");
            } else {
                lblError.setText("");
            }
        });

        VBox inputBox = new VBox(8, new Label("Số tiền yêu cầu nạp (VNĐ)"), txtAmount, quickAmounts, new Label("Ghi chú cho Admin"), txtNote, lblError);
        inputBox.getChildren().get(0).getStyleClass().add("form-label");
        inputBox.getChildren().get(3).getStyleClass().add("form-label");

        VBox infoBox = new VBox(9,
                new Label("✔ Yêu cầu sẽ ở trạng thái Chờ duyệt"),
                new Label("✔ Admin duyệt xong thì ví mới được cộng tiền"),
                new Label("✔ Bạn có thể nhấn Làm mới để đồng bộ số dư"));
        infoBox.getStyleClass().add("info-box");

        root.getChildren().addAll(title, subtitle, inputBox, infoBox);
        pane.setContent(root);
        pane.setPrefWidth(560);

        Button submitBtn = (Button) pane.lookupButton(submitBtnType);
        submitBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        cancelBtn.setText("Hủy bỏ");
        cancelBtn.getStyleClass().add("btn-outline");

        submitBtn.addEventFilter(ActionEvent.ACTION, e -> {
            String text = txtAmount.getText().trim();
            if (text.isEmpty()) {
                lblError.setText("❌ Vui lòng nhập số tiền!");
                e.consume();
                return;
            }
            try {
                double amount = Double.parseDouble(text);
                if (amount <= 0) {
                    lblError.setText("❌ Số tiền phải lớn hơn 0!");
                    e.consume();
                }
            } catch (Exception ex) {
                lblError.setText("❌ Dữ liệu không hợp lệ!");
                e.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != submitBtnType) return null;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", Double.parseDouble(txtAmount.getText().trim()));
            data.put("note", txtNote.getText() == null ? "" : txtNote.getText().trim());
            return data;
        });
        dialog.showAndWait().ifPresent(this::sendDepositRequest);
    }

    private Button quickAmountButton(TextField txtAmount, long amount) {
        Button button = new Button("+" + (amount >= 1_000_000 ? (amount / 1_000_000) + "tr" : (amount / 1000) + "k"));
        button.getStyleClass().add("btn-outline");
        button.setOnAction(e -> txtAmount.setText(String.valueOf(amount)));
        return button;
    }

    private void sendDepositRequest(Map<String, Object> data) {
        ClientMain.registerListener("DEPOSIT_REQUEST_CREATED", payload -> {
            ClientMain.unregisterListener("DEPOSIT_REQUEST_CREATED");
            ClientMain.unregisterListener("DEPOSIT_FAILED");
            Platform.runLater(() -> {
                refreshBalance();
                showInfo("Đã gửi yêu cầu", "✅ Yêu cầu nạp tiền đã được gửi tới Admin.\n\nTiền sẽ chỉ cộng vào ví sau khi Admin duyệt.");
            });
        });

        ClientMain.registerListener("DEPOSIT_FAILED", payload -> {
            ClientMain.unregisterListener("DEPOSIT_REQUEST_CREATED");
            ClientMain.unregisterListener("DEPOSIT_FAILED");
            Platform.runLater(() -> showError("❌ Gửi yêu cầu nạp tiền thất bại!\n\n" + payload));
        });

        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("DEPOSIT", gson.toJson(data)))), "deposit-request").start();
    }

    private void updateDashboardStats() {
        if (lblLiveCount != null) lblLiveCount.setText(String.valueOf(getLiveAuctions().size()));
        if (lblWonCount != null) lblWonCount.setText(String.valueOf(wonAuctions == null ? 0 : wonAuctions.size()));
        updateBalanceLabels(UserSession.getInstance().getBalance());
    }

    private void updateBalanceLabels(double balanceVal) {
        String formatted = VND.format((long) balanceVal) + " đ";
        if (lblBalance != null) lblBalance.setText("💳 Số dư: " + formatted);
        if (lblBalanceCard != null) lblBalanceCard.setText(formatted);
    }

    private void setActiveNav(Button active) {
        for (Button button : List.of(btnNavHome, btnNavLive, btnNavWon, btnNavWallet, btnNavSettings)) {
            if (button == null) continue;
            button.getStyleClass().remove("nav-button-active");
            if (!button.getStyleClass().contains("nav-button")) button.getStyleClass().add("nav-button");
        }
        if (active != null) {
            active.getStyleClass().remove("nav-button");
            if (!active.getStyleClass().contains("nav-button-active")) active.getStyleClass().add("nav-button-active");
        }
    }

    private void showSettingsDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Cài đặt nhanh");
        alert.setHeaderText("AuctionVN - Thiết lập người dùng");
        alert.setContentText("Tài khoản: " + (myUsername == null ? "--" : myUsername)
                + "\nVai trò: " + UserSession.getInstance().getRole()
                + "\n\nBạn có thể dùng thanh tìm kiếm, bộ lọc trạng thái và sắp xếp để quản lý phiên đấu giá nhanh hơn.");
        alert.showAndWait();
    }

    @FXML
    void handleLogout(ActionEvent event) {
        cleanupListeners();
        UserSession.getInstance().logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupListeners() {
        ClientMain.unregisterListener("AUCTION_LIST");
        ClientMain.unregisterListener("BALANCE_DATA");
        ClientMain.unregisterListener("AUCTION_LIST_BY_STATUS");
        ClientMain.unregisterListener("AUCTION_STARTED");
        ClientMain.unregisterListener("AUCTION_CANCELED");
        ClientMain.unregisterListener("AUCTION_FINISHED");
        ClientMain.unregisterListener("ERROR");
        ClientMain.unregisterListener("WON_AUCTIONS");
        ClientMain.unregisterListener("DEPOSIT_REQUEST_CREATED");
        ClientMain.unregisterListener("DEPOSIT_FAILED");
    }

    private int readInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? 0 : Integer.parseInt(value.toString()); }
        catch (Exception e) { return 0; }
    }

    private double readDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? 0 : Double.parseDouble(value.toString()); }
        catch (Exception e) { return 0; }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Lỗi");
        a.setHeaderText(null);
        a.setContentText(message);
        a.show();
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.show();
    }
}
