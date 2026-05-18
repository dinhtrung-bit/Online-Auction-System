package client.controllers;

import client.models.user.UserSession;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import client.utils.UiUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;

/**
 * AuctionDetailController — Điều khiển màn hình phòng đấu giá trực tiếp.
 *
 * <p><b>Refactor:</b>
 * <ul>
 *   <li>Các helper dùng chung (formatVND, showAlert, parseDoubleSafe, addInfoRow,
 *       readonlyTextArea, showCustomDialog, formatDateTime, normalizeCategory,
 *       statusToVietnamese, isTerminalStatus) chuyển sang {@link UiUtils}.
 *   <li>Tách {@code registerServerListeners} thành các phương thức đăng ký theo nhóm.
 *   <li>Đặt tên rõ ràng, xoá comment thừa.
 * </ul>
 */
public class AuctionDetailController implements Initializable {

    // ─── FXML — Bid area ─────────────────────────────────────────────────────
    @FXML private Label lblCurrentPrice, lblWinner, lblTimer;
    @FXML private Label lblRoomTitle, lblRoomId, lblStatusBadge;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid, btnOpenAutoBid;
    @FXML private HBox  paneAutoBidActive;
    @FXML private Label lblAutoBidInfo;

    // ─── FXML — Bid history ──────────────────────────────────────────────────
    @FXML private ListView<String> historyList;
    @FXML private ListView<String> myBidHistoryList;
    @FXML private LineChart<String, Number> bidHistoryChart;

    // ─── FXML — Profile panel ────────────────────────────────────────────────
    @FXML private Label lblAvatar;
    @FXML private Label lblProfileName, lblProfileRole;
    @FXML private Label lblProfileBalance, lblProfileBidCount;
    @FXML private Label lblProfileWins, lblProfileWinRate;
    @FXML private Label lblRankIcon, lblRankTitle, lblRankSub;
    @FXML private Label lblMyBestBid;

    // ─── FXML — Finish overlay ───────────────────────────────────────────────
    @FXML private VBox  overlayFinished;
    @FXML private Label lblFinishIcon, lblFinishTitle, lblFinishMessage;

    // ─── FXML — Product info ─────────────────────────────────────────────────
    @FXML private ImageView imgProduct;
    @FXML private VBox  paneProductImagePlaceholder;
    @FXML private Label lblProductInitial, lblProductName, lblProductCategory, lblProductDescription;
    @FXML private Label lblProductId, lblProductStartingPrice, lblProductStatus, lblSellerInfo;
    @FXML private Label lblAuctionStartTime, lblAuctionEndTime, lblBidStep, lblSuggestedBid;
    @FXML private Label lblTimeHint, lblTotalBidCount, lblParticipantCount, lblLeaderName, lblLeaderPrice;
    @FXML private ProgressBar timerProgress;

    // ─── State ───────────────────────────────────────────────────────────────
    private String  currentRoomId;
    private String  myUsername;
    private volatile int remainingSeconds       = 0;
    private int         initialRemainingSeconds = 0;
    private Timer timer;

    private final Gson gson = new Gson();
    private XYChart.Series<String, Number> priceSeries;

    private int    bidCount          = 0;
    private double currentPriceVal   = 0;
    private double startingPriceVal  = 0;
    private double bidIncrementVal   = 500_000;
    private double suggestedBidVal   = 0;
    private String lastWinner        = "";
    private int    myBidCountInRoom  = 0;
    private double myBestBid         = 0;
    private boolean isAutoBidActive  = false;
    private boolean roomCanBid       = false;
    private boolean finishOverlayShown = false;

    private Map<String, Object>      latestAuctionData = new LinkedHashMap<>();
    private List<Map<String, Object>> latestBidHistory  = new ArrayList<>();
    private String latestImagePath = "";

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        myUsername = UserSession.getInstance().getUsername();
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Diễn biến giá");
        if (bidHistoryChart != null) bidHistoryChart.getData().add(priceSeries);

        setupNumericBidInput();
        setupListPlaceholders();
        setupUserProfile();
        setLoadingState();
        registerServerListeners();
    }

    // ─── UI setup ────────────────────────────────────────────────────────────

    private void setupNumericBidInput() {
        if (txtBidAmount == null) return;
        txtBidAmount.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null) return;
            String sanitized = newVal.replaceAll("[^\\d]", "");
            if (!newVal.equals(sanitized)) txtBidAmount.setText(sanitized);
        });
    }

    private void setupListPlaceholders() {
        if (historyList != null) {
            historyList.setPlaceholder(new Label("Chưa có lượt đặt giá nào."));
            historyList.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    if (!empty) setStyle("-fx-font-weight: 700; -fx-text-fill: #334155;");
                }
            });
        }
        if (myBidHistoryList != null)
            myBidHistoryList.setPlaceholder(new Label("Các lượt đặt giá của bạn sẽ hiện tại đây."));
    }

    private void setupUserProfile() {
        String name = UiUtils.safe(myUsername).isBlank() ? "--" : myUsername;
        if (lblProfileName    != null) lblProfileName.setText(name);
        if (lblProfileRole    != null) lblProfileRole.setText(mapRoleLabel(UserSession.getInstance().getRole()));
        if (lblAvatar         != null) {
            String initials = name.length() >= 2
                    ? (name.substring(0, 1) + name.substring(1, 2)).toUpperCase()
                    : name.toUpperCase();
            lblAvatar.setText(initials);
        }
        if (lblProfileBalance  != null) lblProfileBalance.setText(UiUtils.formatVND(UserSession.getInstance().getBalance()));
        if (lblProfileBidCount != null) lblProfileBidCount.setText("0");
        if (lblProfileWins     != null) lblProfileWins.setText("--");
        if (lblProfileWinRate  != null) lblProfileWinRate.setText("--");
        if (lblRankTitle       != null) lblRankTitle.setText("Đang tải...");
        if (lblRankSub         != null) lblRankSub.setText("");
    }

    private void setLoadingState() {
        if (lblCurrentPrice    != null) lblCurrentPrice.setText("Đang tải...");
        if (lblTimer           != null) lblTimer.setText("--:--:--");
        if (timerProgress      != null) timerProgress.setProgress(0);
        if (lblProductName     != null) lblProductName.setText("Đang tải sản phẩm...");
        if (lblProductDescription != null) lblProductDescription.setText("Đang lấy thông tin sản phẩm từ server...");
        if (lblSuggestedBid    != null) lblSuggestedBid.setText("--");
    }

    // ─── Server listeners ────────────────────────────────────────────────────

    private void registerServerListeners() {
        registerAuctionDataListeners();
        registerBidListeners();
        registerBalanceListener();
    }

    private void registerAuctionDataListeners() {
        ClientMain.registerListener("AUCTION_DETAIL_DATA", payload ->
                Platform.runLater(() -> applyAuctionDetailPayload(payload)));

        ClientMain.registerListener("BID_HISTORY", payload -> {
            try {
                Type lt = new TypeToken<List<Map<String, Object>>>() {}.getType();
                Platform.runLater(() -> applyBidHistory(gson.fromJson(payload, lt)));
            } catch (Exception e) {
                System.err.println("BID_HISTORY err: " + e.getMessage());
            }
        });

        ClientMain.registerListener("WON_AUCTIONS", payload -> {
            try {
                Type lt = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> won = gson.fromJson(payload, lt);
                int wins = won != null ? won.size() : 0;
                Platform.runLater(() -> {
                    if (lblProfileWins != null) lblProfileWins.setText(String.valueOf(wins));
                    int total = wins + myBidCountInRoom;
                    if (lblProfileWinRate != null)
                        lblProfileWinRate.setText(total > 0 ? ((int) ((double) wins / total * 100)) + "%" : "--");
                    updateRankBadge(wins);
                });
            } catch (Exception e) {
                System.err.println("WON_AUCTIONS err: " + e.getMessage());
            }
        });

        ClientMain.registerListener("AUCTION_FINISHED", payload -> {
            if (!isSameRoomPayload(payload)) return;
            Platform.runLater(() -> { refreshRoomData(); showFinishedOverlay(); });
        });

        ClientMain.registerListener("AUCTION_CANCELED", payload -> {
            if (!isSameRoomPayload(payload)) return;
            Platform.runLater(() -> showCanceledOverlay("Quản trị viên hoặc người bán đã hủy phiên đấu giá này."));
        });
    }

    private void registerBidListeners() {
        ClientMain.registerListener("UPDATE_PRICE", payload -> {
            String[] parts = payload.split(":");
            if (parts.length < 3 || currentRoomId == null || !parts[0].equals(currentRoomId)) return;
            Platform.runLater(() -> {
                currentPriceVal = UiUtils.parseDoubleSafe(parts[1], currentPriceVal);
                lastWinner = parts[2];
                updatePriceWidgets();
                appendBidRow(lastWinner, currentPriceVal, "vừa xong");

                Map<String, Object> liveBid = new LinkedHashMap<>();
                liveBid.put("username", lastWinner); liveBid.put("amount", currentPriceVal); liveBid.put("time", "vừa xong");
                latestBidHistory.add(liveBid);

                if (lastWinner.equals(myUsername)) {
                    myBidCountInRoom++;
                    if (myBidHistoryList != null) myBidHistoryList.getItems().add(0, UiUtils.formatVND(currentPriceVal) + "   [vừa xong]");
                    if (currentPriceVal > myBestBid) {
                        myBestBid = currentPriceVal;
                        if (lblMyBestBid != null) lblMyBestBid.setText("Cao nhất: " + UiUtils.formatVND(myBestBid));
                    }
                    if (lblProfileBidCount != null) lblProfileBidCount.setText(String.valueOf(myBidCountInRoom));
                    sendAsync("GET_BALANCE", "");
                }
            });
            refreshRoomDetailOnly();
        });

        ClientMain.registerListener("AUTO_BID_EXCEEDED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            Platform.runLater(() -> {
                deactivateAutoBidUI();
                UiUtils.showAlert(Alert.AlertType.INFORMATION, "AutoBid", "AutoBid đã đạt giới hạn tối đa và tự dừng.");
            });
        });

        ClientMain.registerListener("BID_FAILED", payload ->
                Platform.runLater(() -> { unlockBidButton(); UiUtils.showAlert(Alert.AlertType.WARNING, "Đặt giá thất bại", safeMessage(payload, "Không đặt giá được.")); }));

        ClientMain.registerListener("BID_SUCCESS", payload ->
                Platform.runLater(() -> { unlockBidButton(); showToast("✅ Đặt giá thành công!"); refreshRoomData(); }));

        ClientMain.registerListener("ERROR", payload ->
                Platform.runLater(() -> { unlockBidButton(); UiUtils.showAlert(Alert.AlertType.ERROR, "Lỗi từ máy chủ", safeMessage(payload, "Có lỗi từ máy chủ.")); }));
    }

    private void registerBalanceListener() {
        ClientMain.registerListener("BALANCE_DATA", payload -> {
            try {
                double newBalance = Double.parseDouble(payload.trim());
                UserSession.getInstance().setBalance(newBalance);
                Platform.runLater(() -> {
                    if (lblProfileBalance != null) lblProfileBalance.setText(UiUtils.formatVND(newBalance));
                });
            } catch (Exception ignored) {}
        });
    }

    // ─── Room management ─────────────────────────────────────────────────────

    public void setRoomId(String id) {
        this.currentRoomId = id;
        if (lblRoomId != null) lblRoomId.setText("Phòng #" + id);
        refreshRoomData();
    }

    @FXML void handleRefreshRoom() { refreshRoomData(); showToast("↻ Đang làm mới dữ liệu phòng..."); }

    private void refreshRoomData() {
        if (currentRoomId == null || currentRoomId.isBlank()) return;
        sendAsync("GET_AUCTION_DETAIL", currentRoomId);
        sendAsync("GET_BID_HISTORY", currentRoomId);
        sendAsync("GET_MY_WON_AUCTIONS", "");
        sendAsync("GET_BALANCE", "");
    }

    private void refreshRoomDetailOnly() {
        if (currentRoomId == null || currentRoomId.isBlank()) return;
        sendAsync("GET_AUCTION_DETAIL", currentRoomId);
    }

    private void sendAsync(String action, String payload) {
        new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO(action, payload))), "send-" + action).start();
    }

    // ─── Apply auction data ──────────────────────────────────────────────────

    private void applyAuctionDetailPayload(String payload) {
        try {
            if (payload != null && payload.trim().startsWith("{")) {
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                applyAuctionDetailMap(gson.fromJson(payload, type));
            } else {
                applyLegacyAuctionDetail(payload);
            }
        } catch (Exception e) {
            UiUtils.showAlert(Alert.AlertType.ERROR, "Lỗi dữ liệu",
                    "Không đọc được dữ liệu phòng đấu giá: " + e.getMessage());
        }
    }

    private void applyLegacyAuctionDetail(String payload) {
        String[] parts = payload != null ? payload.split(":") : new String[0];
        if (parts.length < 3) return;
        currentPriceVal   = UiUtils.parseDoubleSafe(parts[0], currentPriceVal);
        remainingSeconds  = UiUtils.parseIntSafe(parts[1], 0);
        initialRemainingSeconds = Math.max(remainingSeconds, 1);
        String status = parts[2];
        roomCanBid = "RUNNING".equalsIgnoreCase(status);
        if (lblProductStatus != null) lblProductStatus.setText(UiUtils.statusToVietnamese(status));
        updateStatusBadge(status);
        updatePriceWidgets();
        if (UiUtils.isTerminalStatus(status)) { handleTerminalStatus(status); return; }
        startTimer();
    }

    private void applyAuctionDetailMap(Map<String, Object> data) {
        if (data == null) return;
        latestAuctionData = new LinkedHashMap<>(data);

        currentPriceVal  = numberFrom(data, "currentPrice", currentPriceVal);
        startingPriceVal = numberFrom(data, "startingPrice", startingPriceVal > 0 ? startingPriceVal : currentPriceVal);
        bidIncrementVal  = numberFrom(data, "bidIncrement", calculateDefaultIncrement(currentPriceVal));
        if (bidIncrementVal <= 0) bidIncrementVal = calculateDefaultIncrement(currentPriceVal);
        suggestedBidVal  = currentPriceVal + bidIncrementVal;

        remainingSeconds = (int) numberFrom(data, "secondsLeft", numberFrom(data, "remainingSeconds", remainingSeconds));
        initialRemainingSeconds = Math.max(remainingSeconds, Math.max(initialRemainingSeconds, 1));

        String status   = stringFrom(data, "status", "UNKNOWN");
        roomCanBid = "RUNNING".equalsIgnoreCase(status);
        String itemName = stringFrom(data, "itemName", stringFrom(data, "name", "Sản phẩm đấu giá"));
        String itemId   = stringFrom(data, "itemId", "--");
        String category = UiUtils.normalizeCategory(stringFrom(data, "category", stringFrom(data, "categoryInfo", "--")));
        String desc     = stringFrom(data, "description", "Chưa có mô tả chi tiết cho sản phẩm này.");
        String imagePath = stringFrom(data, "imagePath", "");
        latestImagePath  = imagePath;
        String seller   = stringFrom(data, "sellerName", "Seller #" + stringFrom(data, "sellerID", "--"));
        String startTime = stringFrom(data, "startTime", "");
        String endTime   = stringFrom(data, "endTime", "");
        lastWinner = stringFrom(data, "currentWinner", lastWinner);

        setText(lblRoomTitle,          "Đấu giá trực tiếp | " + itemName);
        setText(lblProductName,        itemName);
        setText(lblProductId,          itemId);
        setText(lblProductCategory,    "Danh mục: " + category);
        setText(lblProductDescription, desc.isBlank() ? "Chưa có mô tả." : desc);
        setText(lblProductStartingPrice, UiUtils.formatVND(startingPriceVal));
        setText(lblProductStatus,      UiUtils.statusToVietnamese(status));
        setText(lblSellerInfo,         seller);
        setText(lblAuctionStartTime,   UiUtils.formatDateTime(startTime));
        setText(lblAuctionEndTime,     UiUtils.formatDateTime(endTime));
        setText(lblBidStep,            UiUtils.formatVND(bidIncrementVal));
        setText(lblSuggestedBid,       UiUtils.formatVND(suggestedBidVal));

        setProductPlaceholderIcon(category, itemName);
        loadProductImage(imagePath);
        updateStatusBadge(status);
        updatePriceWidgets();

        if (UiUtils.isTerminalStatus(status)) { handleTerminalStatus(status); return; }
        startTimer();
    }

    private void applyBidHistory(List<Map<String, Object>> bids) {
        latestBidHistory = bids != null ? new ArrayList<>(bids) : new ArrayList<>();
        if (historyList     != null) historyList.getItems().clear();
        if (myBidHistoryList != null) myBidHistoryList.getItems().clear();
        if (priceSeries     != null) priceSeries.getData().clear();
        bidCount = myBidCountInRoom = 0; myBestBid = 0;
        Set<String> participants = new LinkedHashSet<>();

        if (bids == null || bids.isEmpty()) {
            if (priceSeries != null && currentPriceVal > 0)
                priceSeries.getData().add(new XYChart.Data<>("Bắt đầu", currentPriceVal));
            updateBidCounters(0); return;
        }

        for (Map<String, Object> bid : bids) {
            String user   = stringFrom(bid, "username", "--");
            double amount = numberFrom(bid, "amount", 0);
            String time   = stringFrom(bid, "time", "");
            if (time.length() > 16) time = time.substring(0, 16);
            participants.add(user);
            appendBidRow(user, amount, time);
            if (user.equals(myUsername)) {
                myBidCountInRoom++;
                if (myBidHistoryList != null) myBidHistoryList.getItems().add(0, UiUtils.formatVND(amount) + "   [" + time + "]");
                if (amount > myBestBid) myBestBid = amount;
            }
            lastWinner = user; currentPriceVal = amount;
        }

        updatePriceWidgets();
        if (lblProfileBidCount != null) lblProfileBidCount.setText(String.valueOf(myBidCountInRoom));
        if (lblMyBestBid != null) lblMyBestBid.setText(myBestBid > 0 ? "Cao nhất: " + UiUtils.formatVND(myBestBid) : "");
        updateBidCounters(participants.size());
    }

    private void appendBidRow(String user, double amount, String time) {
        bidCount++;
        if (historyList != null) historyList.getItems().add(0, "#" + bidCount + "  " + user + "  →  " + UiUtils.formatVND(amount) + "   [" + time + "]");
        if (priceSeries != null) priceSeries.getData().add(new XYChart.Data<>("L" + bidCount, amount));
    }

    private void updateBidCounters(int participantCount) {
        if (lblTotalBidCount    != null) lblTotalBidCount.setText(String.valueOf(bidCount));
        if (lblParticipantCount != null) lblParticipantCount.setText(String.valueOf(Math.max(participantCount, lastWinner.isBlank() ? 0 : 1)));
    }

    private void updatePriceWidgets() {
        bidIncrementVal = bidIncrementVal > 0 ? bidIncrementVal : calculateDefaultIncrement(currentPriceVal);
        suggestedBidVal = currentPriceVal + bidIncrementVal;
        String winnerText = (lastWinner == null || lastWinner.isBlank()) ? "--" : lastWinner;
        setText(lblCurrentPrice, UiUtils.formatVND(currentPriceVal));
        setText(lblWinner,       "Người dẫn đầu: " + winnerText);
        setText(lblLeaderName,   lastWinner == null || lastWinner.isBlank() ? "Chưa có" : lastWinner);
        setText(lblLeaderPrice,  currentPriceVal > 0 ? UiUtils.formatVND(currentPriceVal) : "--");
        setText(lblSuggestedBid, UiUtils.formatVND(suggestedBidVal));
        setText(lblBidStep,      UiUtils.formatVND(bidIncrementVal));
    }

    // ─── Timer ───────────────────────────────────────────────────────────────

    private void startTimer() {
        if (timer != null) timer.cancel();
        updateTimerUI();
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override public void run() {
                Platform.runLater(() -> {
                    if (remainingSeconds > 0) { remainingSeconds--; updateTimerUI(); return; }
                    remainingSeconds = 0;
                    updateTimerUI();
                    if (timer != null) timer.cancel();
                    setText(lblTimeHint, "Đang chốt kết quả...");
                    if (btnPlaceBid  != null) btnPlaceBid.setDisable(true);
                    if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
                    refreshRoomData();
                });
            }
        }, 1000, 1000);
    }

    private void updateTimerUI() {
        if (lblTimer != null) lblTimer.setText(formatSeconds(remainingSeconds));
        if (timerProgress != null) timerProgress.setProgress(
                initialRemainingSeconds <= 0 ? 0 : Math.max(0, Math.min(1, (double) remainingSeconds / initialRemainingSeconds)));
        if (lblTimeHint != null) {
            if (!roomCanBid)           lblTimeHint.setText("Không nhận đặt giá");
            else if (remainingSeconds <= 0)  lblTimeHint.setText("Đang chốt kết quả...");
            else if (remainingSeconds <= 60) lblTimeHint.setText("Sắp kết thúc — hãy đặt giá nhanh");
            else                             lblTimeHint.setText("Đồng bộ thời gian từ server");
        }
    }

    private String formatSeconds(int seconds) {
        int s = Math.max(seconds, 0);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // ─── Bid actions ─────────────────────────────────────────────────────────

    @FXML
    void handlePlaceBid() {
        if (!roomCanBid) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Không thể đặt giá", "Phiên hiện không ở trạng thái đang chạy."); return;
        }
        String text = txtBidAmount != null ? txtBidAmount.getText().trim() : "";
        if (text.isEmpty()) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Thiếu dữ liệu", "Vui lòng nhập số tiền đặt giá!"); return;
        }
        double amount = UiUtils.parseDoubleSafe(text, -1);
        if (amount <= currentPriceVal) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Không hợp lệ", "Giá đặt phải cao hơn giá hiện tại " + UiUtils.formatVND(currentPriceVal) + "."); return;
        }
        if (amount < suggestedBidVal) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Chưa đạt mức đề xuất", "Mức tối thiểu nên đặt là " + UiUtils.formatVND(suggestedBidVal) + "."); return;
        }
        if (amount > UserSession.getInstance().getBalance()) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Số dư không đủ", "Số dư hiện tại: " + UiUtils.formatVND(UserSession.getInstance().getBalance())); return;
        }
        if (currentRoomId == null || currentRoomId.isBlank()) {
            UiUtils.showAlert(Alert.AlertType.ERROR, "Lỗi phiên", "Không xác định được phòng đấu giá."); return;
        }
        lockBidButton();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", UiUtils.parseDoubleSafe(currentRoomId, 0));
        payload.put("amount", amount);
        sendAsync("BID", gson.toJson(payload));
        if (txtBidAmount != null) txtBidAmount.clear();
    }

    private void lockBidButton() {
        if (btnPlaceBid != null) { btnPlaceBid.setDisable(true); btnPlaceBid.setText("Đang gửi..."); }
    }

    private void unlockBidButton() {
        if (btnPlaceBid != null) { btnPlaceBid.setDisable(!roomCanBid); btnPlaceBid.setText("🔨 Đặt giá"); }
    }

    @FXML
    void handleQuickBid(ActionEvent event) {
        if (event == null || !(event.getSource() instanceof Button btn)) return;
        double add = UiUtils.parseDoubleSafe(String.valueOf(btn.getUserData()), bidIncrementVal);
        if (txtBidAmount != null) txtBidAmount.setText(String.valueOf((long) (currentPriceVal + add)));
    }

    @FXML
    void handleUseSuggestedBid() {
        if (txtBidAmount != null) txtBidAmount.setText(String.valueOf((long) suggestedBidVal));
    }

    // ─── Info popups ─────────────────────────────────────────────────────────

    @FXML
    void handleShowProductDetailPopup() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(4));
        Label title = new Label(textOf(lblProductName, "Sản phẩm đấu giá"));
        title.getStyleClass().add("popup-main-title");
        Label subtitle = new Label("Toàn bộ thông tin đang đọc từ dữ liệu phòng đấu giá hiện tại.");
        subtitle.getStyleClass().add("popup-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(10);
        UiUtils.addInfoRow(grid, 0, "Mã sản phẩm",  textOf(lblProductId, "--"));
        UiUtils.addInfoRow(grid, 1, "Danh mục",     textOf(lblProductCategory, "--"));
        UiUtils.addInfoRow(grid, 2, "Trạng thái",   textOf(lblProductStatus, "--"));
        UiUtils.addInfoRow(grid, 3, "Người bán",    textOf(lblSellerInfo, "--"));
        UiUtils.addInfoRow(grid, 4, "Giá khởi điểm", textOf(lblProductStartingPrice, "--"));
        UiUtils.addInfoRow(grid, 5, "Giá hiện tại", textOf(lblCurrentPrice, "--"));
        UiUtils.addInfoRow(grid, 6, "Bước giá",     textOf(lblBidStep, "--"));
        UiUtils.addInfoRow(grid, 7, "Giá đề xuất",  textOf(lblSuggestedBid, "--"));
        UiUtils.addInfoRow(grid, 8, "Bắt đầu",      textOf(lblAuctionStartTime, "--"));
        UiUtils.addInfoRow(grid, 9, "Kết thúc",     textOf(lblAuctionEndTime, "--"));

        Label descTitle = new Label("Mô tả sản phẩm");
        descTitle.getStyleClass().add("section-title");
        content.getChildren().addAll(title, subtitle, grid, descTitle,
                UiUtils.readonlyTextArea(textOf(lblProductDescription, "Chưa có mô tả."), 160));
        UiUtils.showCustomDialog("Thông tin sản phẩm", content, 760, 640);
    }

    @FXML
    void handleShowProductImagePopup() {
        if (imgProduct == null || imgProduct.getImage() == null) {
            UiUtils.showAlert(Alert.AlertType.INFORMATION, "Ảnh sản phẩm", "Sản phẩm này chưa có ảnh hoặc ảnh chưa tải được."); return;
        }
        VBox content = new VBox(14);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(4));
        Label title = new Label(textOf(lblProductName, "Ảnh sản phẩm"));
        title.getStyleClass().add("popup-main-title");
        ImageView preview = new ImageView(imgProduct.getImage());
        preview.setFitWidth(760); preview.setFitHeight(520); preview.setPreserveRatio(true); preview.setSmooth(true);
        preview.getStyleClass().add("popup-image-preview");
        Label path = new Label(latestImagePath == null || latestImagePath.isBlank() ? "Không có đường dẫn ảnh." : latestImagePath);
        path.setWrapText(true); path.getStyleClass().add("popup-subtitle");
        content.getChildren().addAll(title, preview, path);
        UiUtils.showCustomDialog("Xem ảnh sản phẩm", content, 860, 720);
    }

    @FXML
    void handleShowFullBidHistory() {
        VBox content = new VBox(14);
        Label title = new Label("Lịch sử đặt giá đầy đủ");
        title.getStyleClass().add("popup-main-title");
        ListView<String> list = new ListView<>();
        list.setPrefHeight(460);
        if (latestBidHistory == null || latestBidHistory.isEmpty()) {
            list.setPlaceholder(new Label("Chưa có lượt đặt giá nào."));
        } else {
            int index = latestBidHistory.size();
            ListIterator<Map<String, Object>> it = latestBidHistory.listIterator(latestBidHistory.size());
            while (it.hasPrevious()) {
                Map<String, Object> bid = it.previous();
                list.getItems().add("#" + index + "  ·  " + stringFrom(bid, "username", "--")
                        + "  ·  " + UiUtils.formatVND(numberFrom(bid, "amount", 0))
                        + "  ·  " + stringFrom(bid, "time", "--"));
                index--;
            }
        }
        Label summary = new Label("Tổng lượt đặt: " + bidCount + "  ·  Người dẫn đầu: " + (lastWinner.isBlank() ? "--" : lastWinner));
        summary.getStyleClass().add("popup-hint");
        content.getChildren().addAll(title, summary, list);
        UiUtils.showCustomDialog("Lịch sử đặt giá", content, 720, 620);
    }

    @FXML
    void handleShowMyBidHistory() {
        VBox content = new VBox(14);
        Label title = new Label("Các lượt đặt giá của tôi");
        title.getStyleClass().add("popup-main-title");
        ListView<String> list = new ListView<>();
        list.setPrefHeight(420);
        if (myBidHistoryList != null) list.getItems().setAll(myBidHistoryList.getItems());
        list.setPlaceholder(new Label("Bạn chưa đặt giá trong phiên này."));
        Label summary = new Label("Số lượt: " + myBidCountInRoom + "  ·  Mức cao nhất: " + (myBestBid > 0 ? UiUtils.formatVND(myBestBid) : "--"));
        summary.getStyleClass().add("popup-hint");
        content.getChildren().addAll(title, summary, list);
        UiUtils.showCustomDialog("Bid của tôi", content, 620, 560);
    }

    // ─── AutoBid ─────────────────────────────────────────────────────────────

    @FXML
    void handleOpenAutoBidDialog(ActionEvent event) {
        if (!roomCanBid) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "AutoBid", "Chỉ có thể bật AutoBid khi phiên đang chạy."); return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("../views/autobid-dialog.fxml"));
            Parent root = loader.load();
            AutoBidDialogController ctrl = loader.getController();
            ctrl.setup(currentRoomId, currentPriceVal, (mode, cfg) -> {
                ClientMain.registerListener("SET_AUTO_BID_SUCCESS", payload -> {
                    ClientMain.unregisterListener("SET_AUTO_BID_SUCCESS");
                    ClientMain.unregisterListener("SET_AUTO_BID_FAILED");
                    Platform.runLater(() -> { isAutoBidActive = true; activateAutoBidUI(cfg); });
                });
                ClientMain.registerListener("SET_AUTO_BID_FAILED", payload -> {
                    ClientMain.unregisterListener("SET_AUTO_BID_SUCCESS");
                    ClientMain.unregisterListener("SET_AUTO_BID_FAILED");
                    Platform.runLater(() -> UiUtils.showAlert(Alert.AlertType.WARNING, "Kích hoạt AutoBid thất bại",
                            "Server báo: " + safeMessage(payload, "Không bật được AutoBid.")));
                });
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("auctionId", (long) UiUtils.parseDoubleSafe(currentRoomId, 0));
                payload.put("maxBid", (long) cfg.maxBid);
                payload.put("step", (long) cfg.increment);
                sendAsync("SET_AUTO_BID", gson.toJson(payload));
            });
            Stage dialog = new Stage();
            dialog.setTitle("Cài đặt AutoBid");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(((Node) event.getSource()).getScene().getWindow());
            dialog.setScene(new Scene(root, 460, 640));
            dialog.setResizable(false);
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            UiUtils.showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được AutoBid dialog: " + e.getMessage());
        }
    }

    private void activateAutoBidUI(AutoBidDialogController.AutoBidConfig cfg) {
        if (paneAutoBidActive != null) { paneAutoBidActive.setVisible(true); paneAutoBidActive.setManaged(true); }
        if (btnOpenAutoBid != null) {
            btnOpenAutoBid.setText("🤖 Đang bật");
            btnOpenAutoBid.getStyleClass().remove("autobid-button");
            if (!btnOpenAutoBid.getStyleClass().contains("autobid-button-active"))
                btnOpenAutoBid.getStyleClass().add("autobid-button-active");
        }
        String modeLabel = switch (cfg.mode) {
            case "FIXED" -> "Giá cố định"; case "SNIPE" -> "Snipe"; default -> "Tăng dần";
        };
        if (lblAutoBidInfo != null)
            lblAutoBidInfo.setText("Giới hạn: " + UiUtils.formatVND(cfg.maxBid) + "  ·  Bước: " + UiUtils.formatVND(cfg.increment) + "  ·  Chế độ: " + modeLabel);
    }

    @FXML
    void handleCancelAutoBid() {
        ClientMain.registerListener("CANCEL_AUTO_BID_SUCCESS", payload -> {
            ClientMain.unregisterListener("CANCEL_AUTO_BID_SUCCESS");
            ClientMain.unregisterListener("CANCEL_AUTO_BID_FAILED");
            Platform.runLater(this::deactivateAutoBidUI);
        });
        ClientMain.registerListener("CANCEL_AUTO_BID_FAILED", payload -> {
            ClientMain.unregisterListener("CANCEL_AUTO_BID_SUCCESS");
            ClientMain.unregisterListener("CANCEL_AUTO_BID_FAILED");
            Platform.runLater(() -> UiUtils.showAlert(Alert.AlertType.WARNING, "Hủy AutoBid thất bại",
                    "Server báo: " + safeMessage(payload, "Không hủy được AutoBid.")));
        });
        sendAsync("CANCEL_AUTO_BID", currentRoomId);
    }

    private void deactivateAutoBidUI() {
        isAutoBidActive = false;
        if (paneAutoBidActive != null) { paneAutoBidActive.setVisible(false); paneAutoBidActive.setManaged(false); }
        if (btnOpenAutoBid != null) {
            btnOpenAutoBid.setText("🤖 AutoBid");
            btnOpenAutoBid.getStyleClass().remove("autobid-button-active");
            if (!btnOpenAutoBid.getStyleClass().contains("autobid-button"))
                btnOpenAutoBid.getStyleClass().add("autobid-button");
        }
    }

    // ─── Status badge & overlays ─────────────────────────────────────────────

    private void updateStatusBadge(String status) {
        if (lblStatusBadge == null) return;
        String normalized = status == null ? "UNKNOWN" : status.toUpperCase();
        lblStatusBadge.getStyleClass().removeAll("live-status-badge", "live-status-open", "live-status-ended", "live-status-canceled");
        switch (normalized) {
            case "RUNNING"  -> { lblStatusBadge.setText("● Đang chạy");     lblStatusBadge.getStyleClass().add("live-status-badge"); }
            case "OPEN"     -> { lblStatusBadge.setText("○ Sắp bắt đầu");  lblStatusBadge.getStyleClass().add("live-status-open"); }
            case "CANCELED" -> { lblStatusBadge.setText("× Đã hủy");        lblStatusBadge.getStyleClass().add("live-status-canceled"); }
            default         -> { lblStatusBadge.setText("■ Kết thúc");      lblStatusBadge.getStyleClass().add("live-status-ended"); }
        }
        if (btnPlaceBid   != null) btnPlaceBid.setDisable(!roomCanBid);
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(!roomCanBid);
    }

    private void showFinishedOverlay() {
        if (finishOverlayShown) return;
        finishOverlayShown = true; remainingSeconds = 0; roomCanBid = false;
        updateTimerUI();
        if (timer != null) timer.cancel();
        deactivateAutoBidUI();
        if (btnPlaceBid   != null) btnPlaceBid.setDisable(true);
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
        if (overlayFinished != null) { overlayFinished.setVisible(true); overlayFinished.setManaged(true); overlayFinished.toFront(); }

        String winner = UiUtils.safe(lastWinner).trim();
        String me     = UiUtils.safe(myUsername).trim();
        boolean iWin  = !winner.isBlank() && winner.equalsIgnoreCase(me);
        if (iWin) {
            setText(lblFinishIcon,    "🏆");
            setText(lblFinishTitle,   "CHÚC MỪNG CHIẾN THẮNG!");
            setText(lblFinishMessage, "Bạn đã đấu giá thành công với mức giá " + UiUtils.formatVND(currentPriceVal));
        } else {
            setText(lblFinishIcon,  "🛑");
            setText(lblFinishTitle, "PHIÊN ĐẤU GIÁ KẾT THÚC");
            setText(lblFinishMessage, (winner.isBlank() || "Chưa có".equalsIgnoreCase(winner))
                    ? "Phiên đã kết thúc nhưng chưa có người thắng."
                    : "Sản phẩm đã thuộc về " + winner + " với mức giá " + UiUtils.formatVND(currentPriceVal));
        }
        updateStatusBadge("PAID");
    }

    private void showCanceledOverlay(String message) {
        if (timer != null) timer.cancel();
        roomCanBid = false; finishOverlayShown = true;
        unlockBidButton();
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
        deactivateAutoBidUI();
        updateStatusBadge("CANCELED");
        if (overlayFinished != null) { overlayFinished.setVisible(true); overlayFinished.setManaged(true); overlayFinished.toFront(); }
        setText(lblFinishIcon,    "🚫");
        setText(lblFinishTitle,   "PHIÊN ĐẤU GIÁ BỊ HỦY");
        setText(lblFinishMessage, message);
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    @FXML
    void handleBackToList(ActionEvent event) {
        if (timer != null) timer.cancel();
        unregisterDetailListeners();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void unregisterDetailListeners() {
        List.of("AUCTION_DETAIL_DATA", "UPDATE_PRICE", "AUCTION_FINISHED", "AUCTION_CANCELED",
                        "BID_FAILED", "BID_SUCCESS", "ERROR", "AUTO_BID_EXCEEDED", "BID_HISTORY",
                        "WON_AUCTIONS", "BALANCE_DATA",
                        "SET_AUTO_BID_SUCCESS", "SET_AUTO_BID_FAILED",
                        "CANCEL_AUTO_BID_SUCCESS", "CANCEL_AUTO_BID_FAILED")
                .forEach(ClientMain::unregisterListener);
    }

    // ─── Product image ───────────────────────────────────────────────────────

    private void loadProductImage(String path) {
        boolean loaded = false;
        try {
            if (path != null && !path.isBlank() && imgProduct != null) {
                String uri = path.startsWith("http") || path.startsWith("file:") ? path : new File(path).toURI().toString();
                Image image = new Image(uri, true);
                imgProduct.setImage(image);
                loaded = !image.isError();
            }
        } catch (Exception ignored) {}
        if (paneProductImagePlaceholder != null) {
            paneProductImagePlaceholder.setVisible(!loaded);
            paneProductImagePlaceholder.setManaged(!loaded);
        }
    }

    private void setProductPlaceholderIcon(String category, String name) {
        if (lblProductInitial == null) return;
        String c = UiUtils.safe(category).toLowerCase();
        if (c.contains("điện") || c.contains("elect")) lblProductInitial.setText("💻");
        else if (c.contains("xe") || c.contains("vehicle")) lblProductInitial.setText("🚗");
        else if (c.contains("art") || c.contains("nghệ")) lblProductInitial.setText("🏺");
        else if (name != null && !name.isBlank()) lblProductInitial.setText(name.substring(0, 1).toUpperCase());
        else lblProductInitial.setText("📦");
    }

    // ─── Terminal status handling ─────────────────────────────────────────────

    private void handleTerminalStatus(String status) {
        roomCanBid = false; remainingSeconds = 0;
        if (timer != null) timer.cancel();
        updateTimerUI();
        if (btnPlaceBid   != null) btnPlaceBid.setDisable(true);
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
        String s = UiUtils.safe(status).trim().toUpperCase();
        if (s.equals("CANCELED") || s.equals("CANCELLED") || s.equals("ĐÃ HỦY"))
            showCanceledOverlay("Phiên đấu giá đã bị hủy.");
        else
            showFinishedOverlay();
    }

    // ─── Rank badge ──────────────────────────────────────────────────────────

    private void updateRankBadge(int wins) {
        String icon, title, sub;
        if (wins >= 50)      { icon = "💎"; title = "Hạng Kim Cương"; sub = "Top 1% người dùng"; }
        else if (wins >= 20) { icon = "🥇"; title = "Hạng Vàng";      sub = "Top 10% người dùng"; }
        else if (wins >= 5)  { icon = "🥈"; title = "Hạng Bạc";       sub = "Top 30% người dùng"; }
        else                 { icon = "🥉"; title = "Hạng Đồng";      sub = "Người mới tham gia"; }
        if (lblRankIcon  != null) lblRankIcon.setText(icon);
        if (lblRankTitle != null) lblRankTitle.setText(title);
        if (lblRankSub   != null) lblRankSub.setText(sub);
    }

    private String mapRoleLabel(String role) {
        if (role == null) return "Người dùng";
        return switch (role.toUpperCase()) {
            case "BIDDER" -> "Người đấu giá";
            case "SELLER" -> "Người bán";
            case "ADMIN"  -> "Quản trị viên";
            default       -> role;
        };
    }

    // ─── Toast ───────────────────────────────────────────────────────────────

    private void showToast(String message) {
        try {
            if (lblCurrentPrice == null || lblCurrentPrice.getScene() == null) return;
            javafx.stage.Window window = lblCurrentPrice.getScene().getWindow();
            if (window == null) return;
            Popup popup = new Popup();
            Label toast = new Label(message);
            toast.setStyle("-fx-background-color:rgba(15,23,42,0.94); -fx-text-fill:white; -fx-padding:12 22; -fx-background-radius:999; -fx-font-size:13px; -fx-font-weight:bold;");
            popup.getContent().add(toast); popup.setAutoHide(true);
            popup.show(window, window.getX() + window.getWidth() - 330, window.getY() + window.getHeight() - 110);
            PauseTransition delay = new PauseTransition(Duration.seconds(2.3));
            delay.setOnFinished(e -> popup.hide()); delay.play();
        } catch (Exception ignored) {}
    }

    // ─── Utility helpers ─────────────────────────────────────────────────────

    private double calculateDefaultIncrement(double price) {
        if (price < 1_000_000)    return 50_000;
        if (price < 10_000_000)   return 100_000;
        if (price < 100_000_000)  return 500_000;
        return 1_000_000;
    }

    private boolean isSameRoomPayload(String payload) {
        if (currentRoomId == null || currentRoomId.isBlank()) return false;
        if (payload == null || payload.trim().isEmpty()) return false;
        String p = payload.trim();
        if (p.equals(currentRoomId)) return true;
        try { return (long) Double.parseDouble(p) == (long) Double.parseDouble(currentRoomId); }
        catch (Exception ignored) {}
        return p.contains("\"auctionId\":" + currentRoomId) || p.contains("\"roomId\":" + currentRoomId);
    }

    private String safeMessage(String payload, String fallback) {
        return (payload == null || payload.trim().isEmpty() || "null".equalsIgnoreCase(payload.trim())) ? fallback : payload;
    }

    private void setText(Label label, String text) {
        if (label != null) label.setText(text);
    }

    private String textOf(Label label, String fallback) {
        return (label == null || UiUtils.safe(label.getText()).isBlank()) ? fallback : label.getText();
    }

    private String stringFrom(Map<String, Object> m, String key, String fallback) {
        if (m == null || !m.containsKey(key) || m.get(key) == null) return fallback;
        String v = String.valueOf(m.get(key));
        return "null".equalsIgnoreCase(v) ? fallback : v;
    }

    private double numberFrom(Map<String, Object> m, String key, double fallback) {
        if (m == null || !m.containsKey(key) || m.get(key) == null) return fallback;
        return UiUtils.numberFrom(m.get(key), fallback);
    }
}