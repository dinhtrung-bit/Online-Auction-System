package client.controllers;

import client.models.auction.AuctionViewModel;
import client.models.user.UserSession;
import client.services.ServerGateway;
import client.utils.MapAccessor;
import client.utils.MoneyFormatter;
import client.utils.SafeParser;
import client.utils.StatusMapper;
import client.utils.dialogs.Dialogs;
import client.utils.dialogs.WalletDialogs;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * AuctionListController — Danh sách phiên đấu giá dành cho Bidder.
 *
 * <p><b>Refactor v2:</b>
 * <ul>
 *   <li>Toàn bộ dialog nạp tiền + thông báo ví chuyển sang
 *       {@link WalletDialogs}.
 *   <li>Network qua {@link ServerGateway}.
 *   <li>Status badge dùng {@link StatusMapper}.
 *   <li>Số tiền dùng {@link MoneyFormatter}.
 * </ul>
 */
public class AuctionListController implements Initializable {

    // ─── FXML ───────────────────────────────────────────────────────
    @FXML private VBox    auctionContainer;
    @FXML private ToggleButton btnTabLive, btnTabWon;
    @FXML private ComboBox<String> cmbStatus, cmbSort;
    @FXML private TextField txtAuctionSearch;
    @FXML private Label   lblBalance, lblUserName;
    @FXML private Label   lblLiveCount, lblWonCount, lblBalanceCard;
    @FXML private Button  btnNavHome, btnNavLive, btnNavWon, btnNavWallet, btnNavSettings;
    @FXML private ScrollPane scrollAuctions;

    // ─── State ──────────────────────────────────────────────────────
    private ToggleGroup tabGroup;
    private List<AuctionViewModel> allAuctions = new ArrayList<>();
    private List<AuctionViewModel> wonAuctions = new ArrayList<>();
    private String currentTab          = "LIVE";
    private String currentStatusFilter = "TẤT CẢ";
    private String myUsername;

    private static final List<String> LISTENER_ACTIONS = List.of(
            "AUCTION_LIST", "BALANCE_DATA", "AUCTION_LIST_BY_STATUS",
            "AUCTION_STARTED", "AUCTION_CANCELED", "AUCTION_FINISHED",
            "ERROR", "WON_AUCTIONS", "DEPOSIT_SUCCESS", "DEPOSIT_FAILED", "WALLET_ADJUSTED");

    // ─── Lifecycle ──────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        myUsername = UserSession.getInstance().getUsername();
        if (lblUserName != null)
            lblUserName.setText(SafeParser.safe(myUsername).isBlank() ? "Người đấu giá" : myUsername);
        setupTabsAndFilters();
        registerServerListeners();
        loadAuctionsFromServer();
        refreshBalance();
    }

    private void setupTabsAndFilters() {
        tabGroup = new ToggleGroup();
        if (btnTabLive != null) btnTabLive.setToggleGroup(tabGroup);
        if (btnTabWon  != null) btnTabWon.setToggleGroup(tabGroup);

        if (cmbStatus != null) {
            cmbStatus.getItems().setAll("TẤT CẢ", "OPEN", "RUNNING");
            cmbStatus.setValue("TẤT CẢ");
        }
        if (cmbSort != null) {
            cmbSort.getItems().setAll("Mới nhất", "Giá tăng dần", "Giá giảm dần", "Tên A-Z");
            cmbSort.setValue("Mới nhất");
        }
    }

    // ─── Server listeners ───────────────────────────────────────────

    private void registerServerListeners() {
        registerAuctionListeners();
        registerWalletListeners();
        ServerGateway.onString("ERROR", payload -> Dialogs.error("Lỗi", payload));
    }

    private void registerAuctionListeners() {
        ServerGateway.onList("AUCTION_LIST", AuctionViewModel.class, list -> {
            allAuctions = list != null ? list : new ArrayList<>();
            updateDashboardStats();
            applyFilterAndRender();
        });

        ServerGateway.onMapList("WON_AUCTIONS", raw -> {
            List<AuctionViewModel> wonList = new ArrayList<>();
            if (raw != null) {
                for (Map<String, Object> r : raw) {
                    wonList.add(new AuctionViewModel(
                            MapAccessor.getInt(r, "auctionId"),
                            MapAccessor.getString(r, "itemName", ""),
                            MapAccessor.getDouble(r, "finalPrice"),
                            myUsername,
                            MapAccessor.getString(r, "status", "FINISHED")));
                }
            }
            wonAuctions = wonList;
            updateDashboardStats();
            if ("WON".equals(currentTab)) applyFilterAndRender();
        });

        ServerGateway.onString("AUCTION_STARTED",  p -> loadAuctionsFromServer());
        ServerGateway.onString("AUCTION_CANCELED", p -> loadAuctionsFromServer());
        ServerGateway.onString("AUCTION_FINISHED", p -> {
            loadAuctionsFromServer();
            loadWonAuctionsFromServer();
        });
    }

    private void registerWalletListeners() {
        ServerGateway.onString("BALANCE_DATA", payload -> {
            try {
                double bal = Double.parseDouble(payload.trim());
                UserSession.getInstance().setBalance(bal);
                updateBalanceLabels(bal);
            } catch (Exception ignored) {}
        });

        ServerGateway.onMap("WALLET_ADJUSTED", data -> {
            try {
                double delta      = MapAccessor.getDouble(data, "delta");
                double newBalance = MapAccessor.getDouble(data, "newBalance");
                String title      = MapAccessor.getString(data, "title",   "Ví của bạn vừa được cập nhật");
                String message    = MapAccessor.getString(data, "message", "Số dư ví đã thay đổi.");
                String reason     = MapAccessor.getString(data, "reason",  "");
                UserSession.getInstance().setBalance(newBalance);
                updateBalanceLabels(newBalance);
                WalletDialogs.showWalletAdjusted(title, message, delta, newBalance, reason);
            } catch (Exception e) {
                refreshBalance();
                Dialogs.info("Ví của bạn vừa được cập nhật",
                        "Số dư ví đã thay đổi.\nVui lòng bấm Làm mới nếu số dư chưa cập nhật.");
            }
        });
    }

    // ─── Tab & nav ──────────────────────────────────────────────────

    @FXML void switchTab(ActionEvent event) {
        if (btnTabWon != null && btnTabWon.isSelected()) showWonTab(); else showLiveTab();
    }

    @FXML void handleNavHome(ActionEvent event)     { resetFilters(); showLiveTab(); setActiveNav(btnNavHome); }
    @FXML void handleNavLive(ActionEvent event)     { showLiveTab(); setActiveNav(btnNavLive); }
    @FXML void handleNavWon(ActionEvent event)      { showWonTab(); setActiveNav(btnNavWon); }
    @FXML void handleNavWallet(ActionEvent event)   { setActiveNav(btnNavWallet); handleDeposit(event); setActiveNav(btnNavHome); }
    @FXML void handleNavSettings(ActionEvent event) {
        setActiveNav(btnNavSettings);
        showSettingsDialog();
        setActiveNav("WON".equals(currentTab) ? btnNavWon : btnNavHome);
    }

    @FXML void handleRefresh(ActionEvent event) {
        loadAuctionsFromServer();
        if ("WON".equals(currentTab)) loadWonAuctionsFromServer();
        refreshBalance();
    }

    @FXML void handleSearchAuctions()             { applyFilterAndRender(); }
    @FXML void handleSortAuctions(ActionEvent e)  { applyFilterAndRender(); }

    @FXML
    void handleFilterByStatus(ActionEvent event) {
        currentStatusFilter = (cmbStatus == null || cmbStatus.getValue() == null) ? "TẤT CẢ" : cmbStatus.getValue();
        applyFilterAndRender();
    }

    private void showLiveTab() {
        currentTab = "LIVE";
        if (btnTabLive != null) btnTabLive.setSelected(true);
        if (btnTabWon  != null) btnTabWon.setSelected(false);
        if (cmbStatus  != null) cmbStatus.setDisable(false);
        applyFilterAndRender();
        setActiveNav(btnNavLive != null && btnNavLive.isFocused() ? btnNavLive : btnNavHome);
    }

    private void showWonTab() {
        currentTab = "WON";
        if (btnTabWon  != null) btnTabWon.setSelected(true);
        if (btnTabLive != null) btnTabLive.setSelected(false);
        if (cmbStatus  != null) cmbStatus.setDisable(true);
        renderLoading("Đang tải vật phẩm đã thắng...");
        loadWonAuctionsFromServer();
        setActiveNav(btnNavWon);
    }

    private void resetFilters() {
        if (txtAuctionSearch != null) txtAuctionSearch.clear();
        if (cmbSort   != null) cmbSort.setValue("Mới nhất");
        if (cmbStatus != null) cmbStatus.setValue("TẤT CẢ");
        currentStatusFilter = "TẤT CẢ";
    }

    // ─── Filter & sort ──────────────────────────────────────────────

    private void applyFilterAndRender() {
        List<AuctionViewModel> source = "WON".equals(currentTab)
                ? new ArrayList<>(wonAuctions)
                : getLiveAuctions();

        String keyword = txtAuctionSearch == null ? "" : txtAuctionSearch.getText().trim().toLowerCase();
        if (!keyword.isEmpty()) {
            source.removeIf(a -> !SafeParser.safe(a.getItemName()).toLowerCase().contains(keyword)
                    && !String.valueOf(a.getId()).contains(keyword)
                    && !SafeParser.safe(a.getCurrentWinner()).toLowerCase().contains(keyword));
        }
        if (!"WON".equals(currentTab) && !"TẤT CẢ".equals(currentStatusFilter)) {
            source.removeIf(a -> !currentStatusFilter.equalsIgnoreCase(SafeParser.safe(a.getStatus())));
        }
        sortAuctions(source);
        renderAuctionCards(source);
    }

    private List<AuctionViewModel> getLiveAuctions() {
        List<AuctionViewModel> out = new ArrayList<>();
        for (AuctionViewModel a : allAuctions) {
            String s = SafeParser.safe(a.getStatus()).toUpperCase();
            if (!s.equals("FINISHED") && !s.equals("CANCELED") && !s.equals("PAID"))
                out.add(a);
        }
        return out;
    }

    private void sortAuctions(List<AuctionViewModel> source) {
        String sort = (cmbSort == null || cmbSort.getValue() == null) ? "Mới nhất" : cmbSort.getValue();
        switch (sort) {
            case "Giá tăng dần" -> source.sort(Comparator.comparingDouble(AuctionViewModel::getCurrentPrice));
            case "Giá giảm dần" -> source.sort(Comparator.comparingDouble(AuctionViewModel::getCurrentPrice).reversed());
            case "Tên A-Z"      -> source.sort(Comparator.comparing(a -> SafeParser.safe(a.getItemName()).toLowerCase()));
            default             -> source.sort(Comparator.comparingInt(AuctionViewModel::getId).reversed());
        }
    }

    // ─── Server calls ───────────────────────────────────────────────

    private void loadAuctionsFromServer() {
        renderLoading("Đang tải dữ liệu phiên đấu giá...");
        ServerGateway.sendAsync("GET_AVAILABLE_AUCTIONS", "");
    }

    private void loadWonAuctionsFromServer() {
        ServerGateway.sendAsync("GET_MY_WON_AUCTIONS", "");
    }

    private void refreshBalance() {
        ServerGateway.sendAsync("GET_BALANCE", "");
    }

    // ─── Render ─────────────────────────────────────────────────────

    private void renderLoading(String message) {
        if (auctionContainer == null) return;
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
    }

    private void renderAuctionCards(List<AuctionViewModel> list) {
        auctionContainer.getChildren().clear();
        if (scrollAuctions != null) scrollAuctions.setVvalue(0);

        if (list == null || list.isEmpty()) {
            auctionContainer.getChildren().add(buildEmptyState());
            return;
        }
        for (AuctionViewModel auction : list)
            auctionContainer.getChildren().add(buildCard(auction));
    }

    private VBox buildEmptyState() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(52));
        box.getStyleClass().add("empty-state");
        Label icon  = new Label("WON".equals(currentTab) ? "🏆" : "📭");
        icon.setStyle("-fx-font-size: 42px;");
        Label title = new Label("WON".equals(currentTab)
                ? "Bạn chưa trúng vật phẩm nào"
                : "Hiện không có phiên đấu giá phù hợp");
        title.getStyleClass().add("empty-title");
        Label sub   = new Label("Thử đổi bộ lọc hoặc nhấn Làm mới để cập nhật dữ liệu.");
        sub.getStyleClass().add("empty-subtitle");
        box.getChildren().addAll(icon, title, sub);
        return box;
    }

    private HBox buildCard(AuctionViewModel auction) {
        VBox imageBox = buildCardIcon(auction);

        Label lblName   = new Label(SafeParser.safe(auction.getItemName()).isBlank()
                ? "Sản phẩm chưa đặt tên" : auction.getItemName());
        lblName.getStyleClass().add("auction-card-title");
        lblName.setWrapText(true);
        Label lblId     = new Label("Mã phiên #" + auction.getId());
        lblId.getStyleClass().add("auction-card-id");
        Label lblStatus = new Label(StatusMapper.cardBadgeText(auction.getStatus()));
        lblStatus.setStyle(StatusMapper.cardBadgeStyle(auction.getStatus()));

        VBox colPrice  = metricBox("💰 Giá hiện tại",
                MoneyFormatter.format(auction.getCurrentPrice()), "metric-price");
        VBox colWinner = metricBox("👤 Người dẫn đầu",
                SafeParser.safe(auction.getCurrentWinner()).isBlank() ? "Chưa có" : auction.getCurrentWinner(),
                "metric-normal");

        VBox infoBox = new VBox(14, new VBox(7, lblName, lblId, lblStatus),
                new HBox(38, colPrice, colWinner));
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Button btnDetail = new Button("🔍 Xem chi tiết");
        btnDetail.getStyleClass().add("auction-detail-button");
        btnDetail.setUserData(String.valueOf(auction.getId()));
        btnDetail.setOnAction(this::viewDetail);

        HBox card = new HBox(24, imageBox, infoBox, new VBox(8, btnDetail));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("auction-card");
        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) btnDetail.fire(); });
        return card;
    }

    private VBox buildCardIcon(AuctionViewModel auction) {
        VBox box = new VBox();
        box.setPrefSize(92, 92); box.setMinSize(92, 92); box.setMaxSize(92, 92);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("auction-icon-box");
        Label lbl = new Label(StatusMapper.cardIcon(auction.getStatus()));
        lbl.setStyle("-fx-font-size: 34px;");
        box.getChildren().add(lbl);
        return box;
    }

    private VBox metricBox(String title, String value, String valueClass) {
        Label lTitle = new Label(title); lTitle.getStyleClass().add("metric-label");
        Label lValue = new Label(value); lValue.getStyleClass().add(valueClass);
        lValue.setWrapText(true);
        VBox box = new VBox(5, lTitle, lValue);
        box.setMinWidth(150);
        return box;
    }

    // ─── View detail ────────────────────────────────────────────────

    @FXML
    void viewDetail(ActionEvent event) {
        Button btn = (Button) event.getSource();
        String auctionId = btn.getUserData() != null ? btn.getUserData().toString() : btn.getId();
        btn.setDisable(true);
        try {
            ServerGateway.off(LISTENER_ACTIONS.toArray(String[]::new));
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/auction-detail.fxml"));
            Parent root = loader.load();
            AuctionDetailController dc = loader.getController();
            dc.setRoomId(auctionId);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            btn.setDisable(false);
            e.printStackTrace();
            Dialogs.error("Lỗi", "Không mở được chi tiết phiên đấu giá.");
        }
    }

    // ─── Deposit ────────────────────────────────────────────────────

    @FXML
    void handleDeposit(ActionEvent event) {
        java.net.URL css = getClass().getResource("/client/views/app.css");
        WalletDialogs.showDepositDialog(css, newBalance -> updateBalanceLabels(newBalance));
    }

    // ─── Stats & UI helpers ─────────────────────────────────────────

    private void updateDashboardStats() {
        if (lblLiveCount != null) lblLiveCount.setText(String.valueOf(getLiveAuctions().size()));
        if (lblWonCount  != null) lblWonCount.setText(String.valueOf(wonAuctions == null ? 0 : wonAuctions.size()));
        updateBalanceLabels(UserSession.getInstance().getBalance());
    }

    private void updateBalanceLabels(double bal) {
        String formatted = MoneyFormatter.format(bal);
        if (lblBalance     != null) lblBalance.setText("💳 Số dư: " + formatted);
        if (lblBalanceCard != null) lblBalanceCard.setText(formatted);
    }

    private void setActiveNav(Button active) {
        for (Button btn : List.of(btnNavHome, btnNavLive, btnNavWon, btnNavWallet, btnNavSettings)) {
            if (btn == null) continue;
            btn.getStyleClass().remove("nav-button-active");
            if (!btn.getStyleClass().contains("nav-button")) btn.getStyleClass().add("nav-button");
        }
        if (active != null) {
            active.getStyleClass().remove("nav-button");
            if (!active.getStyleClass().contains("nav-button-active"))
                active.getStyleClass().add("nav-button-active");
        }
    }

    private void showSettingsDialog() {
        Dialogs.info("Cài đặt nhanh",
                "Tài khoản: " + SafeParser.safe(myUsername)
                        + "\nVai trò: " + UserSession.getInstance().getRole()
                        + "\n\nBạn có thể dùng thanh tìm kiếm, bộ lọc trạng thái và sắp xếp"
                        + " để quản lý phiên đấu giá nhanh hơn.");
    }

    // ─── Logout ─────────────────────────────────────────────────────

    @FXML
    void handleLogout(ActionEvent event) {
        ServerGateway.off(LISTENER_ACTIONS.toArray(String[]::new));
        UserSession.getInstance().logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
