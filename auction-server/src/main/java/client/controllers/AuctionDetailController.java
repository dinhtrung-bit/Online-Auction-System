package client.controllers;

import client.models.user.UserSession;
import client.services.RequestResponse;
import client.services.ServerGateway;
import client.utils.DateTimes;
import client.utils.MapAccessor;
import client.utils.MoneyFormatter;
import client.utils.SafeParser;
import client.utils.StatusMapper;
import client.utils.dialogs.Dialogs;
import client.utils.dialogs.StyledComponents;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
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
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * AuctionDetailController — Điều khiển màn hình phòng đấu giá trực tiếp.
 *
 * <p><b>Refactor v2:</b>
 * <ul>
 *   <li>Network: tách hoàn toàn qua {@link ServerGateway} và {@link RequestResponse}
 *       — controllers không còn dùng trực tiếp Gson, MessageDTO, hoặc ClientMain.
 *   <li>Format/parse: dùng {@link MoneyFormatter}, {@link DateTimes}, {@link SafeParser},
 *       {@link StatusMapper} thay vì helper rải rác.
 *   <li>Map access: dùng {@link MapAccessor} để đọc payload server.
 *   <li>Dialog: dùng {@link Dialogs} và {@link StyledComponents}.
 * </ul>
 */
public class AuctionDetailController implements Initializable {

    // ─── FXML — Bid area ────────────────────────────────────────────
    @FXML private Label lblCurrentPrice, lblWinner, lblTimer;
    @FXML private Label lblRoomTitle, lblRoomId, lblStatusBadge;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid, btnOpenAutoBid;
    @FXML private HBox  paneAutoBidActive;
    @FXML private Label lblAutoBidInfo;

    // ─── FXML — Bid history ─────────────────────────────────────────
    @FXML private ListView<String> historyList;
    @FXML private ListView<String> myBidHistoryList;
    @FXML private LineChart<String, Number> bidHistoryChart;

    // ─── FXML — Profile panel ───────────────────────────────────────
    @FXML private Label lblAvatar;
    @FXML private Label lblProfileName, lblProfileRole;
    @FXML private Label lblProfileBalance, lblProfileBidCount;
    @FXML private Label lblProfileWins, lblProfileWinRate;
    @FXML private Label lblRankIcon, lblRankTitle, lblRankSub;
    @FXML private Label lblMyBestBid;

    // ─── FXML — Finish overlay ──────────────────────────────────────
    @FXML private VBox  overlayFinished;
    @FXML private Label lblFinishIcon, lblFinishTitle, lblFinishMessage;

    // ─── FXML — Product info ────────────────────────────────────────
    @FXML private ImageView imgProduct;
    @FXML private VBox  paneProductImagePlaceholder;
    @FXML private Label lblProductInitial, lblProductName, lblProductCategory, lblProductDescription;
    @FXML private Label lblProductId, lblProductStartingPrice, lblProductStatus, lblSellerInfo;
    @FXML private Label lblAuctionStartTime, lblAuctionEndTime, lblBidStep, lblSuggestedBid;
    @FXML private Label lblTimeHint, lblTotalBidCount, lblParticipantCount, lblLeaderName, lblLeaderPrice;
    @FXML private ProgressBar timerProgress;

    // ─── State ──────────────────────────────────────────────────────
    private String  currentRoomId;
    private String  myUsername;
    private volatile int remainingSeconds       = 0;
    private int         initialRemainingSeconds = 0;
    private Timer timer;

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

    private static final List<String> LISTENER_ACTIONS = List.of(
            "AUCTION_DETAIL_DATA", "UPDATE_PRICE", "AUCTION_FINISHED", "AUCTION_CANCELED",
            "BID_FAILED", "BID_SUCCESS", "ERROR", "AUTO_BID_EXCEEDED", "BID_HISTORY",
            "WON_AUCTIONS", "BALANCE_DATA",
            "SET_AUTO_BID_SUCCESS", "SET_AUTO_BID_FAILED",
            "CANCEL_AUTO_BID_SUCCESS", "CANCEL_AUTO_BID_FAILED");

    // ─── Lifecycle ──────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Dọn sạch listener cũ trước khi đăng ký mới
        // (tránh tích lũy listener khi controller được khởi tạo lại)
        ServerGateway.off(LISTENER_ACTIONS.toArray(String[]::new));

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

    // ─── UI setup ───────────────────────────────────────────────────

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
        String name = SafeParser.safe(myUsername).isBlank() ? "--" : myUsername;
        setText(lblProfileName,  name);
        setText(lblProfileRole,  mapRoleLabel(UserSession.getInstance().getRole()));
        if (lblAvatar != null) {
            String initials = name.length() >= 2
                    ? (name.substring(0, 1) + name.substring(1, 2)).toUpperCase()
                    : name.toUpperCase();
            lblAvatar.setText(initials);
        }
        setText(lblProfileBalance,  MoneyFormatter.formatViaPattern(UserSession.getInstance().getBalance()));
        setText(lblProfileBidCount, "0");
        setText(lblProfileWins,     "--");
        setText(lblProfileWinRate,  "--");
        setText(lblRankTitle,       "Đang tải...");
        setText(lblRankSub,         "");
    }

    private void setLoadingState() {
        setText(lblCurrentPrice,       "Đang tải...");
        setText(lblTimer,               "--:--:--");
        if (timerProgress != null) timerProgress.setProgress(0);
        setText(lblProductName,        "Đang tải sản phẩm...");
        setText(lblProductDescription, "Đang lấy thông tin sản phẩm từ server...");
        setText(lblSuggestedBid,       "--");
    }

    // ─── Server listeners ───────────────────────────────────────────

    private void registerServerListeners() {
        registerAuctionDataListeners();
        registerBidListeners();
        registerBalanceListener();
    }

    private void registerAuctionDataListeners() {
        ServerGateway.onString("AUCTION_DETAIL_DATA", this::applyAuctionDetailPayload);

        ServerGateway.onMapList("BID_HISTORY", this::applyBidHistory);

        ServerGateway.onMapList("WON_AUCTIONS", won -> {
            int wins = won != null ? won.size() : 0;
            setText(lblProfileWins, String.valueOf(wins));
            int total = wins + myBidCountInRoom;
            setText(lblProfileWinRate, total > 0
                    ? ((int) ((double) wins / total * 100)) + "%"
                    : "--");
            updateRankBadge(wins);
        });

        ServerGateway.onString("AUCTION_FINISHED", payload -> {
            if (!isSameRoomPayload(payload)) return;
            refreshRoomData();
            showFinishedOverlay();
        });

        ServerGateway.onString("AUCTION_CANCELED", payload -> {
            if (!isSameRoomPayload(payload)) return;
            showCanceledOverlay("Quản trị viên hoặc người bán đã hủy phiên đấu giá này.");
        });
    }

    private void registerBidListeners() {
        ServerGateway.onString("UPDATE_PRICE", payload -> {
            String[] parts = payload.split(":");
            if (parts.length < 3 || currentRoomId == null || !parts[0].equals(currentRoomId)) return;
            currentPriceVal = SafeParser.parseDouble(parts[1], currentPriceVal);
            lastWinner = parts[2];
            updatePriceWidgets();

            // Thêm vào latestBidHistory để handleShowFullBidHistory vẫn đúng
            Map<String, Object> liveBid = new LinkedHashMap<>();
            liveBid.put("username", lastWinner);
            liveBid.put("amount",   currentPriceVal);
            liveBid.put("time",     "vừa xong");
            latestBidHistory.add(liveBid);

            // Thêm vào historyList (text) ngay lập tức cho UX nhanh
            // Chart KHÔNG thêm ở đây — để applyBidHistory (từ GET_BID_HISTORY) làm source of truth
            // tránh duplicate/lệch số thứ tự giữa các cửa sổ
            int liveIndex = latestBidHistory.size();
            if (historyList != null)
                historyList.getItems().add(0, "#" + liveIndex + "  " + lastWinner + "  →  "
                        + MoneyFormatter.formatViaPattern(currentPriceVal) + "   [vừa xong]");

            if (lastWinner.equals(myUsername)) {
                myBidCountInRoom++;
                if (myBidHistoryList != null)
                    myBidHistoryList.getItems().add(0,
                            MoneyFormatter.formatViaPattern(currentPriceVal) + "   [vừa xong]");
                if (currentPriceVal > myBestBid) {
                    myBestBid = currentPriceVal;
                    setText(lblMyBestBid, "Cao nhất: " + MoneyFormatter.formatViaPattern(myBestBid));
                }
                setText(lblProfileBidCount, String.valueOf(myBidCountInRoom));
                ServerGateway.sendAsync("GET_BALANCE", "");
            }
            // Refresh để lấy BID_HISTORY mới nhất từ DB → chart sẽ được rebuild chính xác
            ServerGateway.sendAsync("GET_BID_HISTORY", currentRoomId);
            refreshRoomDetailOnly();
        });

        ServerGateway.onString("AUTO_BID_EXCEEDED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            deactivateAutoBidUI();
            Dialogs.info("AutoBid", "AutoBid đã đạt giới hạn tối đa và tự dừng.");
        });

        ServerGateway.onString("BID_FAILED", payload -> {
            unlockBidButton();
            Dialogs.warn("Đặt giá thất bại", safeMessage(payload, "Không đặt giá được."));
        });

        ServerGateway.onString("BID_SUCCESS", payload -> {
            unlockBidButton();
            showToast("✅ Đặt giá thành công!");
            refreshRoomData();
        });

        ServerGateway.onString("ERROR", payload -> {
            unlockBidButton();
            Dialogs.error("Lỗi từ máy chủ", safeMessage(payload, "Có lỗi từ máy chủ."));
        });
    }

    private void registerBalanceListener() {
        ServerGateway.onString("BALANCE_DATA", payload -> {
            try {
                double newBalance = Double.parseDouble(payload.trim());
                UserSession.getInstance().setBalance(newBalance);
                setText(lblProfileBalance, MoneyFormatter.formatViaPattern(newBalance));
            } catch (Exception ignored) {}
        });
    }

    // ─── Room management ────────────────────────────────────────────

    public void setRoomId(String id) {
        this.currentRoomId = id;
        setText(lblRoomId, "Phòng #" + id);
        refreshRoomData();
    }

    @FXML void handleRefreshRoom() {
        refreshRoomData();
        showToast("↻ Đang làm mới dữ liệu phòng...");
    }

    private void refreshRoomData() {
        if (currentRoomId == null || currentRoomId.isBlank()) return;
        ServerGateway.sendAsync("GET_AUCTION_DETAIL",  currentRoomId);
        ServerGateway.sendAsync("GET_BID_HISTORY",     currentRoomId);
        ServerGateway.sendAsync("GET_MY_WON_AUCTIONS", "");
        ServerGateway.sendAsync("GET_BALANCE",         "");
    }

    private void refreshRoomDetailOnly() {
        if (currentRoomId == null || currentRoomId.isBlank()) return;
        ServerGateway.sendAsync("GET_AUCTION_DETAIL", currentRoomId);
    }

    // ─── Apply auction data ─────────────────────────────────────────

    private void applyAuctionDetailPayload(String payload) {
        try {
            if (payload != null && payload.trim().startsWith("{")) {
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType();
                applyAuctionDetailMap(new com.google.gson.Gson().fromJson(payload, type));
            } else {
                applyLegacyAuctionDetail(payload);
            }
        } catch (Exception e) {
            Dialogs.error("Lỗi dữ liệu",
                    "Không đọc được dữ liệu phòng đấu giá: " + e.getMessage());
        }
    }

    private void applyLegacyAuctionDetail(String payload) {
        String[] parts = payload != null ? payload.split(":") : new String[0];
        if (parts.length < 3) return;
        currentPriceVal   = SafeParser.parseDouble(parts[0], currentPriceVal);
        remainingSeconds  = SafeParser.parseInt(parts[1], 0);
        initialRemainingSeconds = Math.max(remainingSeconds, 1);
        String status = parts[2];
        roomCanBid = "RUNNING".equalsIgnoreCase(status);
        setText(lblProductStatus, StatusMapper.toVietnamese(status));
        updateStatusBadge(status);
        updatePriceWidgets();
        if (StatusMapper.isTerminal(status)) { handleTerminalStatus(status); return; }
        startTimer();
    }

    private void applyAuctionDetailMap(Map<String, Object> data) {
        if (data == null) return;
        latestAuctionData = new LinkedHashMap<>(data);

        currentPriceVal  = MapAccessor.getDouble(data, "currentPrice", currentPriceVal);
        startingPriceVal = MapAccessor.getDouble(data, "startingPrice",
                startingPriceVal > 0 ? startingPriceVal : currentPriceVal);
        bidIncrementVal  = MapAccessor.getDouble(data, "bidIncrement",
                calculateDefaultIncrement(currentPriceVal));
        if (bidIncrementVal <= 0) bidIncrementVal = calculateDefaultIncrement(currentPriceVal);
        suggestedBidVal  = currentPriceVal + bidIncrementVal;

        remainingSeconds = (int) MapAccessor.getDouble(data, "secondsLeft",
                MapAccessor.getDouble(data, "remainingSeconds", remainingSeconds));
        initialRemainingSeconds = Math.max(remainingSeconds, Math.max(initialRemainingSeconds, 1));

        String status   = MapAccessor.getString(data, "status", "UNKNOWN");
        roomCanBid = "RUNNING".equalsIgnoreCase(status);
        String itemName = MapAccessor.getString(data, "itemName",
                MapAccessor.getString(data, "name", "Sản phẩm đấu giá"));
        String itemId   = MapAccessor.getString(data, "itemId", "--");
        String category = StatusMapper.normalizeCategory(MapAccessor.getString(data, "category",
                MapAccessor.getString(data, "categoryInfo", "--")));
        String desc     = MapAccessor.getString(data, "description",
                "Chưa có mô tả chi tiết cho sản phẩm này.");
        String imagePath = MapAccessor.getString(data, "imagePath", "");
        latestImagePath  = imagePath;
        String seller   = MapAccessor.getString(data, "sellerName",
                "Seller #" + MapAccessor.getString(data, "sellerID", "--"));
        String startTime = MapAccessor.getString(data, "startTime", "");
        String endTime   = MapAccessor.getString(data, "endTime", "");
        lastWinner = MapAccessor.getString(data, "currentWinner", lastWinner);

        setText(lblRoomTitle,            "Đấu giá trực tiếp | " + itemName);
        setText(lblProductName,          itemName);
        setText(lblProductId,            itemId);
        setText(lblProductCategory,      "Danh mục: " + category);
        setText(lblProductDescription,   desc.isBlank() ? "Chưa có mô tả." : desc);
        setText(lblProductStartingPrice, MoneyFormatter.formatViaPattern(startingPriceVal));
        setText(lblProductStatus,        StatusMapper.toVietnamese(status));
        setText(lblSellerInfo,           seller);
        setText(lblAuctionStartTime,     DateTimes.format(startTime));
        setText(lblAuctionEndTime,       DateTimes.format(endTime));
        setText(lblBidStep,              MoneyFormatter.formatViaPattern(bidIncrementVal));
        setText(lblSuggestedBid,         MoneyFormatter.formatViaPattern(suggestedBidVal));

        setProductPlaceholderIcon(category, itemName);
        loadProductImage(imagePath);
        updateStatusBadge(status);
        updatePriceWidgets();

        if (StatusMapper.isTerminal(status)) { handleTerminalStatus(status); return; }
        startTimer();
    }

    private void applyBidHistory(List<Map<String, Object>> bids) {
        latestBidHistory = bids != null ? new ArrayList<>(bids) : new ArrayList<>();
        // Chart: rebuild hoàn toàn từ DB — đây là source of truth duy nhất
        if (priceSeries != null) priceSeries.getData().clear();
        // historyList và myBidHistoryList chỉ rebuild nếu có data mới từ server
        // (tránh xóa live entries khi GET_BID_HISTORY trả về chưa kịp có bid mới nhất)
        List<String> newHistory   = new ArrayList<>();
        List<String> newMyHistory = new ArrayList<>();
        bidCount = 0;
        int newMyBidCount = 0;
        double newMyBestBid = 0;
        Set<String> participants = new LinkedHashSet<>();

        if (bids == null || bids.isEmpty()) {
            if (priceSeries != null && currentPriceVal > 0)
                priceSeries.getData().add(new XYChart.Data<>("Bắt đầu", currentPriceVal));
            if (historyList     != null) historyList.getItems().clear();
            if (myBidHistoryList != null) myBidHistoryList.getItems().clear();
            myBidCountInRoom = 0;
            myBestBid = 0;
            updateBidCounters(0);
            return;
        }

        for (Map<String, Object> bid : bids) {
            String user   = MapAccessor.getString(bid, "username", "--");
            double amount = MapAccessor.getDouble(bid, "amount");
            String time   = MapAccessor.getString(bid, "time", "");
            if (time.length() > 16) time = time.substring(0, 16);
            participants.add(user);
            bidCount++;
            // Thêm vào chart (source of truth)
            if (priceSeries != null)
                priceSeries.getData().add(new XYChart.Data<>("L" + bidCount, amount));
            newHistory.add(0, "#" + bidCount + "  " + user + "  →  "
                    + MoneyFormatter.formatViaPattern(amount) + "   [" + time + "]");
            if (user.equals(myUsername)) {
                newMyBidCount++;
                newMyHistory.add(0, MoneyFormatter.formatViaPattern(amount) + "   [" + time + "]");
                if (amount > newMyBestBid) newMyBestBid = amount;
            }
            lastWinner = user;
            currentPriceVal = amount;
        }

        // Cập nhật list UI từ data đã rebuild
        if (historyList != null) {
            historyList.getItems().setAll(newHistory);
        }
        if (myBidHistoryList != null) {
            myBidHistoryList.getItems().setAll(newMyHistory);
        }
        myBidCountInRoom = newMyBidCount;
        myBestBid = newMyBestBid;

        updatePriceWidgets();
        setText(lblProfileBidCount, String.valueOf(myBidCountInRoom));
        setText(lblMyBestBid, myBestBid > 0
                ? "Cao nhất: " + MoneyFormatter.formatViaPattern(myBestBid) : "");
        updateBidCounters(participants.size());
    }

    private void updateBidCounters(int participantCount) {
        setText(lblTotalBidCount, String.valueOf(bidCount));
        setText(lblParticipantCount,
                String.valueOf(Math.max(participantCount, lastWinner.isBlank() ? 0 : 1)));
    }

    private void updatePriceWidgets() {
        bidIncrementVal = bidIncrementVal > 0 ? bidIncrementVal : calculateDefaultIncrement(currentPriceVal);
        suggestedBidVal = currentPriceVal + bidIncrementVal;
        String winnerText = (lastWinner == null || lastWinner.isBlank()) ? "--" : lastWinner;
        setText(lblCurrentPrice, MoneyFormatter.formatViaPattern(currentPriceVal));
        setText(lblWinner,       "Người dẫn đầu: " + winnerText);
        setText(lblLeaderName,   lastWinner == null || lastWinner.isBlank() ? "Chưa có" : lastWinner);
        setText(lblLeaderPrice,  currentPriceVal > 0 ? MoneyFormatter.formatViaPattern(currentPriceVal) : "--");
        setText(lblSuggestedBid, MoneyFormatter.formatViaPattern(suggestedBidVal));
        setText(lblBidStep,      MoneyFormatter.formatViaPattern(bidIncrementVal));
    }

    // ─── Timer ──────────────────────────────────────────────────────

    private void startTimer() {
        if (timer != null) timer.cancel();
        updateTimerUI();
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                javafx.application.Platform.runLater(() -> {
                    if (remainingSeconds > 0) { remainingSeconds--; updateTimerUI(); return; }
                    remainingSeconds = 0;
                    updateTimerUI();
                    if (timer != null) timer.cancel();
                    setText(lblTimeHint, "Đang chốt kết quả...");
                    if (btnPlaceBid    != null) btnPlaceBid.setDisable(true);
                    if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
                    refreshRoomData();
                });
            }
        }, 1000, 1000);
    }

    private void updateTimerUI() {
        setText(lblTimer, formatSeconds(remainingSeconds));
        if (timerProgress != null) timerProgress.setProgress(
                initialRemainingSeconds <= 0 ? 0
                        : Math.max(0, Math.min(1, (double) remainingSeconds / initialRemainingSeconds)));
        if (lblTimeHint != null) {
            if (!roomCanBid)                 lblTimeHint.setText("Không nhận đặt giá");
            else if (remainingSeconds <= 0)  lblTimeHint.setText("Đang chốt kết quả...");
            else if (remainingSeconds <= 60) lblTimeHint.setText("Sắp kết thúc — hãy đặt giá nhanh");
            else                             lblTimeHint.setText("Đồng bộ thời gian từ server");
        }
    }

    private String formatSeconds(int seconds) {
        int s = Math.max(seconds, 0);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // ─── Bid actions ────────────────────────────────────────────────

    @FXML
    void handlePlaceBid() {
        if (!roomCanBid) {
            Dialogs.warn("Không thể đặt giá", "Phiên hiện không ở trạng thái đang chạy.");
            return;
        }
        String text = txtBidAmount != null ? txtBidAmount.getText().trim() : "";
        if (text.isEmpty()) {
            Dialogs.warn("Thiếu dữ liệu", "Vui lòng nhập số tiền đặt giá!");
            return;
        }
        double amount = SafeParser.parseDouble(text, -1);
        if (amount <= currentPriceVal) {
            Dialogs.warn("Không hợp lệ",
                    "Giá đặt phải cao hơn giá hiện tại " + MoneyFormatter.formatViaPattern(currentPriceVal) + ".");
            return;
        }
        if (amount < suggestedBidVal) {
            Dialogs.warn("Chưa đạt mức đề xuất",
                    "Mức tối thiểu nên đặt là " + MoneyFormatter.formatViaPattern(suggestedBidVal) + ".");
            return;
        }
        if (amount > UserSession.getInstance().getBalance()) {
            Dialogs.warn("Số dư không đủ",
                    "Số dư hiện tại: " + MoneyFormatter.formatViaPattern(UserSession.getInstance().getBalance()));
            return;
        }
        if (currentRoomId == null || currentRoomId.isBlank()) {
            Dialogs.error("Lỗi phiên", "Không xác định được phòng đấu giá.");
            return;
        }
        lockBidButton();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", SafeParser.parseDouble(currentRoomId, 0));
        payload.put("amount", amount);
        ServerGateway.sendAsync("BID", payload);
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
        double add = SafeParser.parseDouble(String.valueOf(btn.getUserData()), bidIncrementVal);
        if (txtBidAmount != null) txtBidAmount.setText(String.valueOf((long) (currentPriceVal + add)));
    }

    @FXML
    void handleUseSuggestedBid() {
        if (txtBidAmount != null) txtBidAmount.setText(String.valueOf((long) suggestedBidVal));
    }

    // ─── Info popups ────────────────────────────────────────────────

    @FXML
    void handleExpandChart() {
        if (priceSeries == null || priceSeries.getData().isEmpty()) {
            Dialogs.info("Biểu đồ", "Chưa có dữ liệu đặt giá để hiển thị.");
            return;
        }
        try {
            // Tạo chart mới với data hiện tại
            javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
            javafx.scene.chart.NumberAxis   yAxis = new javafx.scene.chart.NumberAxis();
            xAxis.setLabel("Lượt đặt giá");
            yAxis.setLabel("Giá (VNĐ)");
            yAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
                @Override public String toString(Number n) {
                    return MoneyFormatter.formatViaPattern(n.doubleValue());
                }
                @Override public Number fromString(String s) { return 0; }
            });

            LineChart<String, Number> expandedChart = new LineChart<>(xAxis, yAxis);
            expandedChart.setTitle("📈 Diễn biến giá — " + textOf(lblProductName, "Phòng đấu giá"));
            expandedChart.setAnimated(false);
            expandedChart.setLegendVisible(false);
            expandedChart.getStyleClass().add("price-chart");

            // Copy toàn bộ data points từ chart gốc
            XYChart.Series<String, Number> copy = new XYChart.Series<>();
            copy.setName("Diễn biến giá");
            for (XYChart.Data<String, Number> d : priceSeries.getData())
                copy.getData().add(new XYChart.Data<>(d.getXValue(), d.getYValue()));
            expandedChart.getData().add(copy);

            // Thông tin tóm tắt
            Label summary = new Label(
                    "Tổng lượt đặt: " + bidCount
                            + "   ·   Giá cao nhất: " + MoneyFormatter.formatViaPattern(currentPriceVal)
                            + "   ·   Người dẫn đầu: " + (lastWinner.isBlank() ? "--" : lastWinner));
            summary.getStyleClass().add("popup-hint");

            VBox content = new VBox(10, summary, expandedChart);
            VBox.setVgrow(expandedChart, javafx.scene.layout.Priority.ALWAYS);

            Stage chartStage = new Stage();
            chartStage.setTitle("Biểu đồ giá realtime — " + textOf(lblProductName, "Đấu giá"));
            chartStage.setScene(new Scene(content, 960, 560));
            chartStage.setResizable(true);
            chartStage.show();
        } catch (Exception e) {
            Dialogs.error("Lỗi", "Không mở được biểu đồ phóng to: " + e.getMessage());
        }
    }

    // ─── Info popups ────────────────────────────────────────────────

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
        StyledComponents.addInfoRow(grid, 0, "Mã sản phẩm",   textOf(lblProductId, "--"));
        StyledComponents.addInfoRow(grid, 1, "Danh mục",      textOf(lblProductCategory, "--"));
        StyledComponents.addInfoRow(grid, 2, "Trạng thái",    textOf(lblProductStatus, "--"));
        StyledComponents.addInfoRow(grid, 3, "Người bán",     textOf(lblSellerInfo, "--"));
        StyledComponents.addInfoRow(grid, 4, "Giá khởi điểm", textOf(lblProductStartingPrice, "--"));
        StyledComponents.addInfoRow(grid, 5, "Giá hiện tại",  textOf(lblCurrentPrice, "--"));
        StyledComponents.addInfoRow(grid, 6, "Bước giá",      textOf(lblBidStep, "--"));
        StyledComponents.addInfoRow(grid, 7, "Giá đề xuất",   textOf(lblSuggestedBid, "--"));
        StyledComponents.addInfoRow(grid, 8, "Bắt đầu",       textOf(lblAuctionStartTime, "--"));
        StyledComponents.addInfoRow(grid, 9, "Kết thúc",      textOf(lblAuctionEndTime, "--"));

        Label descTitle = new Label("Mô tả sản phẩm");
        descTitle.getStyleClass().add("section-title");
        content.getChildren().addAll(title, subtitle, grid, descTitle,
                StyledComponents.readonlyArea(textOf(lblProductDescription, "Chưa có mô tả."), 160));
        StyledComponents.showScrollable("Thông tin sản phẩm", content, 760, 640);
    }

    @FXML
    void handleShowProductImagePopup() {
        if (imgProduct == null || imgProduct.getImage() == null) {
            Dialogs.info("Ảnh sản phẩm", "Sản phẩm này chưa có ảnh hoặc ảnh chưa tải được.");
            return;
        }
        VBox content = new VBox(14);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(4));
        Label title = new Label(textOf(lblProductName, "Ảnh sản phẩm"));
        title.getStyleClass().add("popup-main-title");
        ImageView preview = new ImageView(imgProduct.getImage());
        preview.setFitWidth(760); preview.setFitHeight(520);
        preview.setPreserveRatio(true); preview.setSmooth(true);
        preview.getStyleClass().add("popup-image-preview");
        Label path = new Label(latestImagePath == null || latestImagePath.isBlank()
                ? "Không có đường dẫn ảnh." : latestImagePath);
        path.setWrapText(true);
        path.getStyleClass().add("popup-subtitle");
        content.getChildren().addAll(title, preview, path);
        StyledComponents.showScrollable("Xem ảnh sản phẩm", content, 860, 720);
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
                list.getItems().add("#" + index + "  ·  " + MapAccessor.getString(bid, "username", "--")
                        + "  ·  " + MoneyFormatter.formatViaPattern(MapAccessor.getDouble(bid, "amount"))
                        + "  ·  " + MapAccessor.getString(bid, "time", "--"));
                index--;
            }
        }
        Label summary = new Label("Tổng lượt đặt: " + bidCount
                + "  ·  Người dẫn đầu: " + (lastWinner.isBlank() ? "--" : lastWinner));
        summary.getStyleClass().add("popup-hint");
        content.getChildren().addAll(title, summary, list);
        StyledComponents.showScrollable("Lịch sử đặt giá", content, 720, 620);
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
        Label summary = new Label("Số lượt: " + myBidCountInRoom
                + "  ·  Mức cao nhất: " + (myBestBid > 0 ? MoneyFormatter.formatViaPattern(myBestBid) : "--"));
        summary.getStyleClass().add("popup-hint");
        content.getChildren().addAll(title, summary, list);
        StyledComponents.showScrollable("Bid của tôi", content, 620, 560);
    }

// ─── AutoBid ────────────────────────────────────────────────────

    @FXML
    void handleOpenAutoBidDialog(ActionEvent event) {
        if (!roomCanBid) {
            Dialogs.warn("AutoBid", "Chỉ có thể bật AutoBid khi phiên đang chạy.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("../views/autobid-dialog.fxml"));
            Parent root = loader.load();
            AutoBidDialogController ctrl = loader.getController();
            ctrl.setup(currentRoomId, currentPriceVal, (mode, cfg) -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("auctionId", (long) SafeParser.parseDouble(currentRoomId, 0));
                payload.put("maxBid",    (long) cfg.maxBid);
                payload.put("step",      (long) cfg.increment);

                RequestResponse.exchange()
                        .request("SET_AUTO_BID", new com.google.gson.Gson().toJson(payload))
                        .onSuccess(p -> { isAutoBidActive = true; activateAutoBidUI(cfg); })
                        .onFailed(p -> Dialogs.warn("Kích hoạt AutoBid thất bại",
                                "Server báo: " + safeMessage(p, "Không bật được AutoBid.")))
                        .send();
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
            Dialogs.error("Lỗi", "Không mở được AutoBid dialog: " + e.getMessage());
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
            case "FIXED" -> "Giá cố định";
            case "SNIPE" -> "Snipe";
            default      -> "Tăng dần";
        };
        setText(lblAutoBidInfo,
                "Giới hạn: " + MoneyFormatter.formatViaPattern(cfg.maxBid)
                        + "  ·  Bước: " + MoneyFormatter.formatViaPattern(cfg.increment)
                        + "  ·  Chế độ: " + modeLabel);
    }

    @FXML
    void handleCancelAutoBid() {
        RequestResponse.exchange()
                .request("CANCEL_AUTO_BID", currentRoomId)
                .onSuccess(p -> deactivateAutoBidUI())
                .onFailed(p -> Dialogs.warn("Hủy AutoBid thất bại",
                        "Server báo: " + safeMessage(p, "Không hủy được AutoBid.")))
                .send();
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

// ─── Status badge & overlays ────────────────────────────────────

    private void updateStatusBadge(String status) {
        if (lblStatusBadge == null) return;
        String normalized = status == null ? "UNKNOWN" : status.toUpperCase();
        lblStatusBadge.getStyleClass().removeAll(
                "live-status-badge", "live-status-open", "live-status-ended", "live-status-canceled");
        switch (normalized) {
            case "RUNNING"  -> { lblStatusBadge.setText("● Đang chạy");    lblStatusBadge.getStyleClass().add("live-status-badge"); }
            case "OPEN"     -> { lblStatusBadge.setText("○ Sắp bắt đầu");  lblStatusBadge.getStyleClass().add("live-status-open"); }
            case "CANCELED" -> { lblStatusBadge.setText("× Đã hủy");       lblStatusBadge.getStyleClass().add("live-status-canceled"); }
            default         -> { lblStatusBadge.setText("■ Kết thúc");      lblStatusBadge.getStyleClass().add("live-status-ended"); }
        }
        if (btnPlaceBid    != null) btnPlaceBid.setDisable(!roomCanBid);
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(!roomCanBid);
    }

    private void showFinishedOverlay() {
        if (finishOverlayShown) return;
        finishOverlayShown = true;
        remainingSeconds = 0;
        roomCanBid = false;
        updateTimerUI();
        if (timer != null) timer.cancel();
        deactivateAutoBidUI();
        if (btnPlaceBid    != null) btnPlaceBid.setDisable(true);
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
        if (overlayFinished != null) {
            overlayFinished.setVisible(true);
            overlayFinished.setManaged(true);
            overlayFinished.toFront();
        }

        String winner = SafeParser.safe(lastWinner).trim();
        String me     = SafeParser.safe(myUsername).trim();
        boolean iWin  = !winner.isBlank() && winner.equalsIgnoreCase(me);
        if (iWin) {
            setText(lblFinishIcon,    "🏆");
            setText(lblFinishTitle,   "CHÚC MỪNG CHIẾN THẮNG!");
            setText(lblFinishMessage, "Bạn đã đấu giá thành công với mức giá "
                    + MoneyFormatter.formatViaPattern(currentPriceVal));
        } else {
            setText(lblFinishIcon,  "🛑");
            setText(lblFinishTitle, "PHIÊN ĐẤU GIÁ KẾT THÚC");
            setText(lblFinishMessage, (winner.isBlank() || "Chưa có".equalsIgnoreCase(winner))
                    ? "Phiên đã kết thúc nhưng chưa có người thắng."
                    : "Sản phẩm đã thuộc về " + winner + " với mức giá "
                    + MoneyFormatter.formatViaPattern(currentPriceVal));
        }
        updateStatusBadge("PAID");
    }

    private void showCanceledOverlay(String message) {
        if (timer != null) timer.cancel();
        roomCanBid = false;
        finishOverlayShown = true;
        unlockBidButton();
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
        deactivateAutoBidUI();
        updateStatusBadge("CANCELED");
        if (overlayFinished != null) {
            overlayFinished.setVisible(true);
            overlayFinished.setManaged(true);
            overlayFinished.toFront();
        }
        setText(lblFinishIcon,    "🚫");
        setText(lblFinishTitle,   "PHIÊN ĐẤU GIÁ BỊ HỦY");
        setText(lblFinishMessage, message);
    }

// ─── Navigation ─────────────────────────────────────────────────

    @FXML
    void handleBackToList(ActionEvent event) {
        if (timer != null) timer.cancel();
        ServerGateway.off(LISTENER_ACTIONS.toArray(String[]::new));
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }

// ─── Product image ──────────────────────────────────────────────

    private void loadProductImage(String path) {
        boolean loaded = false;
        try {
            if (path != null && !path.isBlank() && imgProduct != null) {
                String uri = path.startsWith("http") || path.startsWith("file:")
                        ? path : new File(path).toURI().toString();
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
        String c = SafeParser.safe(category).toLowerCase();
        if (c.contains("điện") || c.contains("elect"))    lblProductInitial.setText("💻");
        else if (c.contains("xe")   || c.contains("vehicle")) lblProductInitial.setText("🚗");
        else if (c.contains("art")  || c.contains("nghệ"))    lblProductInitial.setText("🏺");
        else if (name != null && !name.isBlank()) lblProductInitial.setText(name.substring(0, 1).toUpperCase());
        else lblProductInitial.setText("📦");
    }

// ─── Terminal status handling ───────────────────────────────────

    private void handleTerminalStatus(String status) {
        roomCanBid = false;
        remainingSeconds = 0;
        if (timer != null) timer.cancel();
        updateTimerUI();
        if (btnPlaceBid    != null) btnPlaceBid.setDisable(true);
        if (btnOpenAutoBid != null) btnOpenAutoBid.setDisable(true);
        String s = SafeParser.safe(status).trim().toUpperCase();
        if (s.equals("CANCELED") || s.equals("CANCELLED") || s.equals("ĐÃ HỦY"))
            showCanceledOverlay("Phiên đấu giá đã bị hủy.");
        else
            showFinishedOverlay();
    }

// ─── Rank badge ─────────────────────────────────────────────────

    private void updateRankBadge(int wins) {
        String icon, title, sub;
        if      (wins >= 50) { icon = "💎"; title = "Hạng Kim Cương"; sub = "Top 1% người dùng"; }
        else if (wins >= 20) { icon = "🥇"; title = "Hạng Vàng";       sub = "Top 10% người dùng"; }
        else if (wins >= 5)  { icon = "🥈"; title = "Hạng Bạc";        sub = "Top 30% người dùng"; }
        else                 { icon = "🥉"; title = "Hạng Đồng";       sub = "Người mới tham gia"; }
        setText(lblRankIcon,  icon);
        setText(lblRankTitle, title);
        setText(lblRankSub,   sub);
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

// ─── Toast ──────────────────────────────────────────────────────

    private void showToast(String message) {
        try {
            if (lblCurrentPrice == null || lblCurrentPrice.getScene() == null) return;
            javafx.stage.Window window = lblCurrentPrice.getScene().getWindow();
            if (window == null) return;
            Popup popup = new Popup();
            Label toast = new Label(message);
            toast.setStyle("-fx-background-color:rgba(15,23,42,0.94); -fx-text-fill:white;"
                    + " -fx-padding:12 22; -fx-background-radius:999; -fx-font-size:13px; -fx-font-weight:bold;");
            popup.getContent().add(toast);
            popup.setAutoHide(true);
            popup.show(window,
                    window.getX() + window.getWidth() - 330,
                    window.getY() + window.getHeight() - 110);
            PauseTransition delay = new PauseTransition(Duration.seconds(2.3));
            delay.setOnFinished(e -> popup.hide());
            delay.play();
        } catch (Exception ignored) {}
    }

// ─── Utility helpers ────────────────────────────────────────────

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
        return p.contains("\"auctionId\":" + currentRoomId)
                || p.contains("\"roomId\":" + currentRoomId);
    }

    private String safeMessage(String payload, String fallback) {
        return (payload == null || payload.trim().isEmpty() || "null".equalsIgnoreCase(payload.trim()))
                ? fallback : payload;
    }

    private void setText(Label label, String text) {
        if (label != null) label.setText(text);
    }

    private String textOf(Label label, String fallback) {
        return (label == null || SafeParser.safe(label.getText()).isBlank())
                ? fallback : label.getText();
    }
}