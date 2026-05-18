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
        ClientMain.registerListener("WALLET_ADJUSTED", payload -> Platform.runLater(() -> {
            try {
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> data = gson.fromJson(payload, type);

                double delta = readDouble(data.get("delta"));
                double newBalance = readDouble(data.get("newBalance"));
                String title = String.valueOf(data.getOrDefault("title", "Ví của bạn vừa được cập nhật"));
                String message = String.valueOf(data.getOrDefault("message", "Số dư ví đã thay đổi."));
                String reason = String.valueOf(data.getOrDefault("reason", ""));

                UserSession.getInstance().setBalance(newBalance);
                updateBalanceLabels(newBalance);

                showWalletAdjustedDialog(title, message, delta, newBalance, reason);

            } catch (Exception e) {
                refreshBalance();
                showInfo("Ví của bạn vừa được cập nhật",
                        "Số dư ví đã thay đổi.\nVui lòng bấm Làm mới nếu số dư chưa cập nhật.");
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
        // ── Header ──────────────────────────────────────────────
        StackPane iconWrap = new StackPane(new Label("💳"));
        ((Label) iconWrap.getChildren().get(0)).setStyle("-fx-font-size:26px;");
        iconWrap.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #10B981, #059669);" +
                        "-fx-background-radius: 18;" +
                        "-fx-min-width:54; -fx-max-width:54; -fx-min-height:54; -fx-max-height:54;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(16,185,129,0.38), 16, 0, 0, 5);");

        Label lblTitle = new Label("Nạp tiền vào ví");
        lblTitle.setStyle("-fx-font-size:22px; -fx-font-weight:900; -fx-text-fill:#0F172A;");
        Label lblSub = new Label("Số dư sẽ được cộng ngay lập tức sau khi nạp.");
        lblSub.setStyle("-fx-font-size:13px; -fx-text-fill:#64748B;");

        VBox headerText = new VBox(4, lblTitle, lblSub);
        HBox header = new HBox(16, iconWrap, headerText);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #F8FAFC, #ECFDF5);" +
                        "-fx-padding: 22 24 20 24;" +
                        "-fx-border-color: transparent transparent #E2E8F0 transparent;" +
                        "-fx-border-width: 0 0 1 0;");

        // ── Số dư hiện tại ───────────────────────────────────────
        double curBalance = UserSession.getInstance().getBalance();
        Label lblBalanceTitle = new Label("SỐ DƯ HIỆN TẠI");
        lblBalanceTitle.setStyle("-fx-font-size:11px; -fx-font-weight:900; -fx-text-fill:#64748B;");
        Label lblBalanceVal = new Label(VND.format((long) curBalance) + " đ");
        lblBalanceVal.setStyle("-fx-font-size:28px; -fx-font-weight:900; -fx-text-fill:#059669;");

        VBox balanceCard = new VBox(4, lblBalanceTitle, lblBalanceVal);
        balanceCard.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #FFFFFF, #ECFDF5);" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: #A7F3D0;" +
                        "-fx-border-radius: 18;" +
                        "-fx-padding: 16 20;");

        // ── Input số tiền ────────────────────────────────────────
        Label lblAmountHint = new Label("Số tiền nạp (VNĐ)");
        lblAmountHint.setStyle("-fx-font-size:13px; -fx-font-weight:900; -fx-text-fill:#334155;");

        TextField txtAmount = new TextField();
        txtAmount.setPromptText("Nhập số tiền...");
        txtAmount.setPrefHeight(52);
        txtAmount.setStyle(
                "-fx-font-size:20px; -fx-font-weight:900;" +
                        "-fx-background-color:#F8FAFC; -fx-border-color:#CBD5E1;" +
                        "-fx-background-radius:14; -fx-border-radius:14;" +
                        "-fx-padding: 10 16; -fx-text-fill:#0F172A;" +
                        "-fx-prompt-text-fill:#94A3B8;");

        // Hiển thị số tiền đã format bên dưới input
        Label lblFormatted = new Label(" ");
        lblFormatted.setStyle("-fx-font-size:13px; -fx-font-weight:800; -fx-text-fill:#059669;");

        Label lblError = new Label();
        lblError.setStyle("-fx-font-size:13px; -fx-font-weight:800; -fx-text-fill:#EF4444;");

        txtAmount.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                txtAmount.setText(newVal.replaceAll("[^\\d]", ""));
                return;
            }
            lblError.setText("");
            if (newVal.isEmpty()) {
                lblFormatted.setText(" ");
            } else {
                try {
                    long val = Long.parseLong(newVal);
                    lblFormatted.setText("= " + VND.format(val) + " đ");
                } catch (NumberFormatException ignored) {
                    lblFormatted.setText(" ");
                }
            }
        });

        // ── Quick-amount buttons ─────────────────────────────────
        long[][] presets = {{500_000, 500}, {1_000_000, 1_000}, {2_000_000, 2_000}, {5_000_000, 5_000}, {10_000_000, 10_000}, {50_000_000, 50_000}};
        String[] labels = {"+500k", "+1tr", "+2tr", "+5tr", "+10tr", "+50tr"};
        HBox quickRow = new HBox(8);
        quickRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < presets.length; i++) {
            final long val = presets[i][0];
            Button btn = new Button(labels[i]);
            btn.setStyle(
                    "-fx-background-color:#ECFDF5; -fx-text-fill:#047857;" +
                            "-fx-border-color:#A7F3D0; -fx-border-radius:999;" +
                            "-fx-background-radius:999; -fx-font-weight:900;" +
                            "-fx-font-size:12px; -fx-padding: 7 13; -fx-cursor:hand;");
            btn.setOnMouseEntered(e -> btn.setStyle(
                    "-fx-background-color:#D1FAE5; -fx-text-fill:#065F46;" +
                            "-fx-border-color:#6EE7B7; -fx-border-radius:999;" +
                            "-fx-background-radius:999; -fx-font-weight:900;" +
                            "-fx-font-size:12px; -fx-padding: 7 13; -fx-cursor:hand;"));
            btn.setOnMouseExited(e -> btn.setStyle(
                    "-fx-background-color:#ECFDF5; -fx-text-fill:#047857;" +
                            "-fx-border-color:#A7F3D0; -fx-border-radius:999;" +
                            "-fx-background-radius:999; -fx-font-weight:900;" +
                            "-fx-font-size:12px; -fx-padding: 7 13; -fx-cursor:hand;"));
            btn.setOnAction(e -> {
                String cur = txtAmount.getText().trim();
                long curVal = 0;
                if (!cur.isEmpty()) {
                    try { curVal = Long.parseLong(cur); } catch (NumberFormatException ignored) {}
                }
                txtAmount.setText(String.valueOf(curVal + val));
            });
            quickRow.getChildren().add(btn);
        }

        // ── Preview sau nạp ──────────────────────────────────────
        Label lblAfterTitle = new Label("SAU KHI NẠP");
        lblAfterTitle.setStyle("-fx-font-size:11px; -fx-font-weight:900; -fx-text-fill:#64748B;");
        Label lblAfterVal = new Label(VND.format((long) curBalance) + " đ");
        lblAfterVal.setStyle("-fx-font-size:22px; -fx-font-weight:900; -fx-text-fill:#2563EB;");

        txtAmount.textProperty().addListener((obs, oldVal, newVal) -> {
            long add = 0;
            if (!newVal.isEmpty()) {
                try { add = Long.parseLong(newVal); } catch (NumberFormatException ignored) {}
            }
            lblAfterVal.setText(VND.format((long) curBalance + add) + " đ");
        });

        VBox afterCard = new VBox(4, lblAfterTitle, lblAfterVal);
        afterCard.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #FFFFFF, #EFF6FF);" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: #BFDBFE;" +
                        "-fx-border-radius: 18;" +
                        "-fx-padding: 14 20;");

        HBox cardsRow = new HBox(12, balanceCard, afterCard);
        HBox.setHgrow(balanceCard, Priority.ALWAYS);
        HBox.setHgrow(afterCard, Priority.ALWAYS);
        balanceCard.setMaxWidth(Double.MAX_VALUE);
        afterCard.setMaxWidth(Double.MAX_VALUE);

        VBox inputSection = new VBox(8, lblAmountHint, txtAmount, lblFormatted, quickRow, lblError);

        VBox body = new VBox(16, cardsRow, inputSection);
        body.setStyle("-fx-padding: 22 24;");

        // ── Footer buttons ───────────────────────────────────────
        Button btnCancel = new Button("Hủy");
        btnCancel.setStyle(
                "-fx-background-color:#FFFFFF; -fx-border-color:#CBD5E1;" +
                        "-fx-border-radius:14; -fx-background-radius:14;" +
                        "-fx-text-fill:#475569; -fx-font-weight:900;" +
                        "-fx-font-size:14px; -fx-padding: 12 24; -fx-cursor:hand;");

        Button btnSubmit = new Button("💰  Nạp ngay");
        btnSubmit.setStyle(
                "-fx-background-color: linear-gradient(to right, #10B981, #059669);" +
                        "-fx-text-fill:white; -fx-font-weight:900; -fx-font-size:14px;" +
                        "-fx-background-radius:14; -fx-padding: 12 28; -fx-cursor:hand;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(16,185,129,0.32), 14, 0, 0, 5);");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, spacer, btnCancel, btnSubmit);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle(
                "-fx-background-color:#F8FAFC;" +
                        "-fx-padding: 16 24 20 24;" +
                        "-fx-border-color: #E2E8F0 transparent transparent transparent;" +
                        "-fx-border-width: 1 0 0 0;");

        VBox root = new VBox(header, body, footer);
        root.setStyle(
                "-fx-background-color:white;" +
                        "-fx-background-radius:24;" +
                        "-fx-border-radius:24;" +
                        "-fx-border-color:#E2E8F0;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(15,23,42,0.18), 34, 0.16, 0, 14);");

        // ── Stage ────────────────────────────────────────────────
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        // apply app.css
        try {
            java.net.URL css = getClass().getResource("/client/views/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}

        Stage popup = new Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        popup.setTitle("Nạp tiền vào ví");
        popup.setScene(scene);
        popup.setWidth(500);
        popup.setResizable(false);

        btnCancel.setOnAction(e -> popup.close());
        btnSubmit.setOnAction(e -> {
            String text = txtAmount.getText().trim();
            if (text.isEmpty()) {
                lblError.setText("❌ Vui lòng nhập số tiền!");
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                lblError.setText("❌ Dữ liệu không hợp lệ!");
                return;
            }
            if (amount <= 0) {
                lblError.setText("❌ Số tiền phải lớn hơn 0!");
                return;
            }
            if (amount > 1_000_000_000) {
                lblError.setText("❌ Tối đa 1.000.000.000 đ mỗi lần nạp!");
                return;
            }
            popup.close();
            executeDeposit(amount);
        });

        // Enter để submit
        txtAmount.setOnAction(e -> btnSubmit.fire());

        popup.show();
        // Focus vào ô nhập ngay
        Platform.runLater(txtAmount::requestFocus);
    }

    private void executeDeposit(double amount) {
        ClientMain.registerListener("DEPOSIT_SUCCESS", payload -> {
            ClientMain.unregisterListener("DEPOSIT_SUCCESS");
            ClientMain.unregisterListener("DEPOSIT_FAILED");
            Platform.runLater(() -> {
                try {
                    com.google.gson.reflect.TypeToken<java.util.Map<String, Object>> tt =
                            new com.google.gson.reflect.TypeToken<>() {};
                    java.util.Map<String, Object> result = gson.fromJson(payload, tt.getType());
                    if (result != null && result.get("newBalance") instanceof Number n) {
                        double newBal = n.doubleValue();
                        UserSession.getInstance().setBalance(newBal);
                        updateBalanceLabels(newBal);
                    } else {
                        refreshBalance();
                    }
                } catch (Exception ignored) {
                    refreshBalance();
                }
                showInfo("Nạp tiền thành công",
                        "✅ Đã nạp " + VND.format((long) amount) + " đ vào ví!\n\nSố dư đã được cập nhật.");
            });
        });

        ClientMain.registerListener("DEPOSIT_FAILED", payload -> {
            ClientMain.unregisterListener("DEPOSIT_SUCCESS");
            ClientMain.unregisterListener("DEPOSIT_FAILED");
            Platform.runLater(() -> showError("❌ Nạp tiền thất bại!\n\n" + payload));
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount);
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("DEPOSIT", gson.toJson(data)))),
                "deposit").start();
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
        ClientMain.unregisterListener("DEPOSIT_SUCCESS");
        ClientMain.unregisterListener("DEPOSIT_FAILED");
        ClientMain.unregisterListener("WALLET_ADJUSTED");
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
    private void showWalletAdjustedDialog(String title, String message, double delta, double newBalance, String reason) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông báo ví");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

        boolean isPlus = delta >= 0;

        Label icon = new Label(isPlus ? "💰" : "⚠️");
        icon.setStyle(
                "-fx-font-size: 34px;" +
                        "-fx-background-color: " + (isPlus ? "#DCFCE7" : "#FEE2E2") + ";" +
                        "-fx-background-radius: 999;" +
                        "-fx-min-width: 64;" +
                        "-fx-min-height: 64;" +
                        "-fx-alignment: center;"
        );

        Label lblTitle = new Label(title);
        lblTitle.setWrapText(true);
        lblTitle.setStyle(
                "-fx-font-size: 20px;" +
                        "-fx-font-weight: 900;" +
                        "-fx-text-fill: #0F172A;"
        );

        Label lblMessage = new Label(message);
        lblMessage.setWrapText(true);
        lblMessage.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-font-weight: 700;" +
                        "-fx-text-fill: #475569;"
        );

        VBox titleBox = new VBox(6, lblTitle, lblMessage);
        HBox header = new HBox(16, icon, titleBox);
        header.setAlignment(Pos.CENTER_LEFT);

        String sign = isPlus ? "+" : "-";
        Label lblDeltaTitle = new Label("BIẾN ĐỘNG");
        lblDeltaTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 900; -fx-text-fill: #64748B;");

        Label lblDelta = new Label(sign + VND.format((long) Math.abs(delta)) + " đ");
        lblDelta.setStyle(
                "-fx-font-size: 24px;" +
                        "-fx-font-weight: 900;" +
                        "-fx-text-fill: " + (isPlus ? "#059669" : "#DC2626") + ";"
        );

        VBox deltaCard = new VBox(4, lblDeltaTitle, lblDelta);
        deltaCard.setStyle(
                "-fx-background-color: " + (isPlus ? "#ECFDF5" : "#FEF2F2") + ";" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: " + (isPlus ? "#A7F3D0" : "#FECACA") + ";" +
                        "-fx-border-radius: 18;" +
                        "-fx-padding: 16 20;"
        );

        Label lblBalanceTitle = new Label("SỐ DƯ MỚI");
        lblBalanceTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 900; -fx-text-fill: #64748B;");

        Label lblNewBalance = new Label(VND.format((long) newBalance) + " đ");
        lblNewBalance.setStyle(
                "-fx-font-size: 24px;" +
                        "-fx-font-weight: 900;" +
                        "-fx-text-fill: #2563EB;"
        );

        VBox balanceCard = new VBox(4, lblBalanceTitle, lblNewBalance);
        balanceCard.setStyle(
                "-fx-background-color: #EFF6FF;" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: #BFDBFE;" +
                        "-fx-border-radius: 18;" +
                        "-fx-padding: 16 20;"
        );

        HBox cards = new HBox(12, deltaCard, balanceCard);
        HBox.setHgrow(deltaCard, Priority.ALWAYS);
        HBox.setHgrow(balanceCard, Priority.ALWAYS);
        deltaCard.setMaxWidth(Double.MAX_VALUE);
        balanceCard.setMaxWidth(Double.MAX_VALUE);

        VBox body = new VBox(18, header, cards);

        if (reason != null && !reason.isBlank() && !"null".equalsIgnoreCase(reason)) {
            Label lblReasonTitle = new Label("LÝ DO ĐIỀU CHỈNH");
            lblReasonTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 900; -fx-text-fill: #64748B;");

            Label lblReason = new Label(reason);
            lblReason.setWrapText(true);
            lblReason.setStyle(
                    "-fx-font-size: 13px;" +
                            "-fx-text-fill: #334155;" +
                            "-fx-background-color: #F8FAFC;" +
                            "-fx-background-radius: 14;" +
                            "-fx-border-color: #E2E8F0;" +
                            "-fx-border-radius: 14;" +
                            "-fx-padding: 12 14;"
            );

            body.getChildren().addAll(lblReasonTitle, lblReason);
        }

        body.setPadding(new Insets(22));
        body.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 22;"
        );

        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 22;" +
                        "-fx-border-radius: 22;" +
                        "-fx-border-color: #E2E8F0;"
        );

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.setText("Đã hiểu");
            okButton.setStyle(
                    "-fx-background-color: linear-gradient(to right, #2563EB, #7C3AED);" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: 900;" +
                            "-fx-background-radius: 12;" +
                            "-fx-padding: 10 22;" +
                            "-fx-cursor: hand;"
            );
        }

        dialog.show();
    }
}