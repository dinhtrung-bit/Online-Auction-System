package client.controllers;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import client.models.user.UserSession;
import client.models.user.UserViewModel;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import client.utils.UiUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * AdminDashboardController — Quản lý màn hình quản trị viên.
 *
 * <p>Dashboard gồm 4 tab:
 * <ul>
 *   <li>Tổng quan — Số liệu cơ bản và danh sách user.
 *   <li>Người dùng — Quản lý tài khoản, điều chỉnh ví.
 *   <li>Phiên đấu giá — Kiểm duyệt và hủy phiên.
 *   <li>Cài đặt — Thiết lập phí sàn, giới hạn nạp, thời lượng mặc định.
 * </ul>
 *
 * <p><b>Refactor:</b>
 * <ul>
 *   <li>Toàn bộ helper (formatMoney, showAlert, parseMoneyInput) chuyển sang {@link UiUtils}.
 *   <li>Tách {@code setupUserTable} / {@code setupAuctionTable} rõ ràng.
 *   <li>Xoá inline-style magic string trùng lặp vào hằng số.
 *   <li>Đặt tên biến địa phương nhất quán (camelCase).
 * </ul>
 */
public class AdminDashboardController implements Initializable {

    // ─── FXML — Top bar ──────────────────────────────────────────────────────
    @FXML private Label lblPageTitle;
    @FXML private Label lblTableTitle;

    // ─── FXML — Sidebar ──────────────────────────────────────────────────────
    @FXML private Button btnOverview;
    @FXML private Button btnUsers;
    @FXML private Button btnAuctions;
    @FXML private Button btnSettings;

    // ─── FXML — Stat cards ───────────────────────────────────────────────────
    @FXML private Label lblTotalUsers;
    @FXML private Label lblTotalItems;

    // ─── FXML — User filter & action bar ────────────────────────────────────
    @FXML private HBox filterBar;
    @FXML private HBox userActionBar;
    @FXML private HBox auctionActionBar;
    @FXML private TextField txtSearchUser;
    @FXML private ComboBox<String> cmbRoleFilter;

    // ─── FXML — Users table ──────────────────────────────────────────────────
    @FXML private TableView<UserViewModel> tableUsers;
    @FXML private TableColumn<UserViewModel, Integer> colId;
    @FXML private TableColumn<UserViewModel, String>  colUsername;
    @FXML private TableColumn<UserViewModel, String>  colRole;
    @FXML private TableColumn<UserViewModel, String>  colBalance;
    @FXML private TableColumn<UserViewModel, String>  colStatus;

    // ─── FXML — Auctions table ───────────────────────────────────────────────
    @FXML private TableView<Map<String, Object>> tableAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionId;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionItem;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionPrice;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionWinner;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionStatus;

    // ─── FXML — Settings pane ────────────────────────────────────────────────
    @FXML private VBox      settingsPane;
    @FXML private TextField txtPlatformFee;
    @FXML private TextField txtDepositLimit;
    @FXML private TextField txtDefaultDuration;
    @FXML private Label     lblSettingMessage;

    // ─── Style constants ─────────────────────────────────────────────────────
    private static final String STYLE_CARD_LABEL =
            "-fx-font-size: 11px; -fx-font-weight: 900; -fx-text-fill: #64748B;";
    private static final String STYLE_CARD_VALUE_GREEN =
            "-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #059669;";
    private static final String STYLE_CARD_VALUE_BLUE =
            "-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #2563EB;";
    private static final String STYLE_CARD_VALUE_RED =
            "-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #DC2626;";

    // ─── State ───────────────────────────────────────────────────────────────
    private final Gson gson = new Gson();
    private List<UserViewModel>      cachedUsers    = new ArrayList<>();
    private List<Map<String, Object>> cachedAuctions = new ArrayList<>();

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupUserTable();
        setupAuctionTable();
        setupDefaultSettings();
        registerListeners();
        showOverview(null);
        Platform.runLater(this::loadData);
    }

    // ─── Table setup ─────────────────────────────────────────────────────────

    private void setupUserTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colBalance.setCellValueFactory(data ->
                new SimpleStringProperty(UiUtils.formatMoney(data.getValue().getBalance())));

        cmbRoleFilter.getItems().setAll("TẤT CẢ", "ADMIN", "SELLER", "BIDDER");
        cmbRoleFilter.setValue("TẤT CẢ");

        tableUsers.setRowFactory(tv -> {
            TableRow<UserViewModel> row = new TableRow<>();
            MenuItem adjustItem = new MenuItem("💳 Điều chỉnh ví");
            adjustItem.setOnAction(e -> handleAdjustSelectedUserBalance(null));
            ContextMenu menu = new ContextMenu(adjustItem);
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private void setupAuctionTable() {
        colAuctionId.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(getInt(data.getValue(), "id"))));
        colAuctionItem.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("itemName", "N/A"))));
        colAuctionPrice.setCellValueFactory(data ->
                new SimpleStringProperty(UiUtils.formatMoney(getDouble(data.getValue(), "currentPrice"))));
        colAuctionWinner.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("currentWinner", "Chưa có"))));
        colAuctionStatus.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("status", "N/A"))));

        tableAuctions.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            MenuItem cancelItem  = new MenuItem("🚫 Hủy phiên đấu giá");
            cancelItem.setOnAction(e -> cancelSelectedAuction(row.getItem()));
            MenuItem refreshItem = new MenuItem("🔄 Tải lại dữ liệu");
            refreshItem.setOnAction(e -> loadData());
            ContextMenu menu = new ContextMenu(cancelItem, refreshItem);
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private void setupDefaultSettings() {
        txtPlatformFee.setText("5");
        txtDepositLimit.setText("100000000");
        txtDefaultDuration.setText("30");
    }

    // ─── Network ─────────────────────────────────────────────────────────────

    private void registerListeners() {
        ClientMain.registerListener("USER_LIST", payload -> {
            Type listType = new TypeToken<List<UserViewModel>>() {}.getType();
            List<UserViewModel> list = gson.fromJson(payload, listType);
            Platform.runLater(() -> {
                cachedUsers = list != null ? list : new ArrayList<>();
                applyUserFilter();
                lblTotalUsers.setText(String.valueOf(cachedUsers.size()));
            });
        });

        ClientMain.registerListener("ADMIN_STATS", payload -> {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> stats = gson.fromJson(payload, type);
            Platform.runLater(() -> {
                lblTotalUsers.setText(String.valueOf((long) getDouble(stats, "totalUsers")));
                lblTotalItems.setText(String.valueOf((long) getDouble(stats, "totalItems")));
            });
        });

        ClientMain.registerListener("AUCTION_LIST", payload -> {
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> list = gson.fromJson(payload, type);
            Platform.runLater(() -> {
                cachedAuctions = list != null ? list : new ArrayList<>();
                tableAuctions.setItems(FXCollections.observableArrayList(cachedAuctions));
            });
        });

        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_SUCCESS", payload ->
                Platform.runLater(() -> {
                    UiUtils.showAlert(Alert.AlertType.INFORMATION, "Thành công", payload);
                    loadData();
                }));
        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_FAILED", payload ->
                Platform.runLater(() -> UiUtils.showAlert(Alert.AlertType.ERROR, "Lỗi", payload)));

        ClientMain.registerListener("ADMIN_BALANCE_UPDATED", payload ->
                Platform.runLater(() -> {
                    UiUtils.showAlert(Alert.AlertType.INFORMATION, "Đã cập nhật",
                            extractMessage(payload, "Đã điều chỉnh số dư."));
                    loadData();
                }));
        ClientMain.registerListener("ADMIN_BALANCE_FAILED", payload ->
                Platform.runLater(() ->
                        UiUtils.showAlert(Alert.AlertType.ERROR, "Không thể điều chỉnh ví", payload)));
        ClientMain.registerListener("ERROR", payload ->
                Platform.runLater(() -> UiUtils.showAlert(Alert.AlertType.ERROR, "Lỗi", payload)));
    }

    private void loadData() {
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_USERS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ADMIN_STATS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_AUCTIONS", "")));
    }

    // ─── Tab navigation ──────────────────────────────────────────────────────

    @FXML void handleReload(ActionEvent event)       { loadData(); }

    @FXML void handleSearchUser()                    { applyUserFilter(); }

    @FXML
    void handleClearUserFilter(ActionEvent event) {
        txtSearchUser.clear();
        cmbRoleFilter.setValue("TẤT CẢ");
        applyUserFilter();
    }

    private void applyUserFilter() {
        String keyword = txtSearchUser == null ? "" : txtSearchUser.getText().trim().toLowerCase();
        String role    = cmbRoleFilter == null || cmbRoleFilter.getValue() == null
                ? "TẤT CẢ" : cmbRoleFilter.getValue();

        List<UserViewModel> filtered = new ArrayList<>();
        for (UserViewModel user : cachedUsers) {
            boolean matchKeyword = keyword.isEmpty()
                    || user.getUsername().toLowerCase().contains(keyword)
                    || String.valueOf(user.getId()).contains(keyword);
            boolean matchRole = "TẤT CẢ".equals(role) || role.equalsIgnoreCase(user.getRole());
            if (matchKeyword && matchRole) filtered.add(user);
        }
        tableUsers.setItems(FXCollections.observableArrayList(filtered));
    }

    @FXML
    void showOverview(ActionEvent event) {
        lblPageTitle.setText("Tổng quan hệ thống");
        lblTableTitle.setText("Danh sách người dùng mới nhất");
        showUserTable();
        updateSidebar("overview");
    }

    @FXML
    void showUsers(ActionEvent event) {
        lblPageTitle.setText("Quản lý người dùng");
        lblTableTitle.setText("Danh sách tài khoản trong hệ thống");
        showUserTable();
        updateSidebar("users");
    }

    @FXML
    void showAuctions(ActionEvent event) {
        lblPageTitle.setText("Kiểm duyệt phiên đấu giá");
        lblTableTitle.setText("Danh sách phiên đấu giá trong hệ thống");
        showOnly(tableAuctions);
        setVisible(filterBar, false);
        setVisible(userActionBar, false);
        setVisible(auctionActionBar, true);
        updateSidebar("auctions");
    }

    @FXML
    void showSettings(ActionEvent event) {
        lblPageTitle.setText("Cài đặt hệ thống");
        lblTableTitle.setText("Thiết lập hệ thống");
        showSettingsPane();
        updateSidebar("settings");
    }

    private void showUserTable() {
        showOnly(tableUsers);
        setVisible(filterBar, true);
        setVisible(userActionBar, true);
        setVisible(auctionActionBar, false);
        applyUserFilter();
    }

    private void showOnly(TableView<?> table) {
        setVisible(tableUsers,    table == tableUsers);
        setVisible(tableAuctions, table == tableAuctions);
        setVisible(settingsPane,  false);
    }

    private void showSettingsPane() {
        setVisible(tableUsers,       false);
        setVisible(tableAuctions,    false);
        setVisible(filterBar,        false);
        setVisible(userActionBar,    false);
        setVisible(auctionActionBar, false);
        setVisible(settingsPane,     true);
    }

    private void setVisible(Node node, boolean visible) {
        if (node == null) return;
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // ─── User actions ────────────────────────────────────────────────────────

    @FXML
    void handleAdjustSelectedUserBalance(ActionEvent event) {
        UserViewModel selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Chưa chọn người dùng",
                    "Hãy chọn người dùng cần điều chỉnh ví.");
            return;
        }
        showAdjustBalanceDialog(selected);
    }

    /**
     * Xây dựng và hiển thị dialog điều chỉnh ví.
     * Tách riêng khỏi handler để dễ test và đọc.
     */
    private void showAdjustBalanceDialog(UserViewModel user) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Điều chỉnh ví người dùng");

        ButtonType okType = new ButtonType("Lưu điều chỉnh", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        double currentBalance = user.getBalance();

        // — Header —
        Label iconLabel = buildIconLabel("💳");
        Label titleLabel = buildBoldLabel("Điều chỉnh ví người dùng", "22px", "#0F172A");
        Label subLabel   = buildSubLabel("Cộng hoặc trừ tiền trực tiếp vào ví của tài khoản được chọn.");
        HBox header = new HBox(16, iconLabel, new VBox(4, titleLabel, subLabel));
        header.setAlignment(Pos.CENTER_LEFT);

        // — User card —
        Label userBadge     = buildBadge("USER #" + user.getId(), "#2563EB", "#DBEAFE");
        Label usernameLbl   = buildBoldLabel(user.getUsername(), "20px", "#111827");
        Label roleBadge     = buildBadge(user.getRole(), "#7C3AED", "#F3E8FF");
        HBox  userTop       = new HBox(8, userBadge, roleBadge);
        userTop.setAlignment(Pos.CENTER_LEFT);
        VBox userCard = styledCard("#F8FAFC", "#E2E8F0", new VBox(8, userTop, usernameLbl));

        // — Balance cards —
        Label afterValue = buildBoldLabel(UiUtils.formatMoney(currentBalance), "24px", "#2563EB");
        VBox currentCard = balanceCard("SỐ DƯ HIỆN TẠI",
                buildBoldLabel(UiUtils.formatMoney(currentBalance), "24px", "#059669"),
                "#ECFDF5", "#A7F3D0");
        VBox afterCard = balanceCard("SỐ DƯ SAU ĐIỀU CHỈNH", afterValue, "#EFF6FF", "#BFDBFE");
        HBox balanceRow  = stretchHBox(12, currentCard, afterCard);

        // — Delta input —
        Label deltaLabel = buildBoldLabel("Số tiền cộng/trừ", "13px", "#334155");
        TextField txtDelta = styledTextField("VD: 1000000 hoặc -500000");
        Label deltaHint  = buildSubLabel("Nhập số dương để cộng tiền, số âm để trừ tiền.");
        Label previewLbl = buildBoldLabel("Biến động: 0 đ", "13px", "#64748B");

        // — Reason input —
        Label reasonLabel = buildBoldLabel("Lý do điều chỉnh", "13px", "#334155");
        TextArea txtReason = new TextArea();
        txtReason.setPromptText("Ví dụ: Cộng tiền khuyến mãi, hoàn tiền phiên bị hủy...");
        txtReason.setPrefRowCount(4);
        txtReason.setWrapText(true);

        // — Live preview listener —
        txtDelta.textProperty().addListener((obs, old, newVal) -> {
            try {
                double delta = parseMoneyInput(newVal);
                double after = currentBalance + delta;
                afterValue.setText(UiUtils.formatMoney(after));
                afterValue.setStyle(buildBoldStyle("24px", after < 0 ? "#DC2626" : "#2563EB"));
                previewLbl.setText(delta > 0
                        ? "Biến động: +" + UiUtils.formatMoney(delta)
                        : delta < 0
                        ? "Biến động: -" + UiUtils.formatMoney(Math.abs(delta))
                        : "Biến động: 0 đ");
                previewLbl.setStyle(buildBoldStyle("13px",
                        delta > 0 ? "#059669" : delta < 0 ? "#DC2626" : "#64748B"));
            } catch (Exception e) {
                afterValue.setText("Không hợp lệ");
                afterValue.setStyle(buildBoldStyle("24px", "#DC2626"));
                previewLbl.setText("Vui lòng nhập số hợp lệ.");
                previewLbl.setStyle(buildBoldStyle("13px", "#DC2626"));
            }
        });

        VBox form = styledCard("white", "#E2E8F0",
                new VBox(10, deltaLabel, txtDelta, deltaHint, previewLbl,
                        new Separator(), reasonLabel, txtReason));
        VBox root = new VBox(18, header, userCard, balanceRow, form);
        root.setPadding(new Insets(22));
        root.setPrefWidth(620);

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefWidth(680);

        styleDialogButton((Button) dialog.getDialogPane().lookupButton(okType),
                "💾 Lưu điều chỉnh",
                "linear-gradient(to right, #2563EB, #7C3AED)", "white");
        styleDialogButton((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL),
                "Hủy", "#F1F5F9", "#334155");

        dialog.setResultConverter(button -> {
            if (button != okType) return null;
            try {
                double delta = parseMoneyInput(txtDelta.getText());
                double after = currentBalance + delta;
                if (delta == 0) {
                    UiUtils.showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                            "Số tiền điều chỉnh phải khác 0.");
                    return null;
                }
                if (after < 0) {
                    UiUtils.showAlert(Alert.AlertType.WARNING, "Số dư không đủ",
                            "Không thể trừ quá số dư hiện tại của người dùng.");
                    return null;
                }
                String reason = txtReason.getText() == null ? "" : txtReason.getText().trim();
                if (reason.isBlank()) reason = delta > 0 ? "Admin cộng tiền vào ví" : "Admin trừ tiền khỏi ví";

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("userId", user.getId());
                data.put("delta", delta);
                data.put("reason", reason);
                return data;
            } catch (NumberFormatException e) {
                UiUtils.showAlert(Alert.AlertType.ERROR, "Sai định dạng",
                        "Số tiền phải là số hợp lệ. Ví dụ: 1000000 hoặc -500000.");
                return null;
            }
        });

        dialog.showAndWait().ifPresent(data ->
                ClientMain.send(gson.toJson(new MessageDTO("ADMIN_ADJUST_BALANCE", gson.toJson(data)))));
    }

    // ─── Auction actions ─────────────────────────────────────────────────────

    @FXML
    void handleCancelSelectedAuction(ActionEvent event) {
        cancelSelectedAuction(tableAuctions.getSelectionModel().getSelectedItem());
    }

    private void cancelSelectedAuction(Map<String, Object> selected) {
        if (selected == null) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Chưa chọn phiên",
                    "Hãy chọn phiên đấu giá cần hủy.");
            return;
        }
        String status = String.valueOf(selected.getOrDefault("status", ""));
        if ("PAID".equalsIgnoreCase(status)) {
            UiUtils.showAlert(Alert.AlertType.WARNING, "Không thể hủy",
                    "Phiên đã thanh toán, không thể hủy.");
            return;
        }
        int auctionId = getInt(selected, "id");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy phiên");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn hủy phiên đấu giá #" + auctionId + " không?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK)
                ClientMain.send(gson.toJson(new MessageDTO("ADMIN_CANCEL_AUCTION", String.valueOf(auctionId))));
        });
    }

    // ─── Settings ────────────────────────────────────────────────────────────

    @FXML
    void handleSaveSettings(ActionEvent event) {
        try {
            double fee          = Double.parseDouble(txtPlatformFee.getText().trim());
            long   depositLimit = Long.parseLong(txtDepositLimit.getText().trim());
            int    duration     = Integer.parseInt(txtDefaultDuration.getText().trim());

            if (fee < 0 || fee > 20) {
                UiUtils.showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Phí sàn phải từ 0 đến 20%."); return;
            }
            if (depositLimit <= 0) {
                UiUtils.showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Giới hạn nạp tiền phải lớn hơn 0."); return;
            }
            if (duration <= 0) {
                UiUtils.showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Thời gian đấu giá phải lớn hơn 0 phút."); return;
            }

            lblSettingMessage.setText("Đã lưu: phí sàn " + fee + "%, giới hạn nạp "
                    + UiUtils.formatMoney(depositLimit) + ", thời gian mặc định " + duration + " phút.");
            lblSettingMessage.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            UiUtils.showAlert(Alert.AlertType.INFORMATION, "Lưu thành công",
                    "Cài đặt hệ thống đã được lưu cho phiên làm việc hiện tại.");
        } catch (NumberFormatException e) {
            UiUtils.showAlert(Alert.AlertType.ERROR, "Sai định dạng",
                    "Vui lòng chỉ nhập số. Không nhập chữ hoặc ký tự đặc biệt.");
        }
    }

    @FXML
    void handleResetSettings(ActionEvent event) {
        setupDefaultSettings();
        lblSettingMessage.setText("Đã đặt lại cấu hình mặc định.");
        lblSettingMessage.setStyle("-fx-text-fill: #64748b;");
    }

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    private void updateSidebar(String tab) {
        for (Button btn : new Button[]{btnOverview, btnUsers, btnAuctions, btnSettings}) {
            if (btn == null) continue;
            btn.getStyleClass().remove("sidebar-btn-active");
            if (!btn.getStyleClass().contains("sidebar-btn")) btn.getStyleClass().add("sidebar-btn");
        }
        Button active = switch (tab) {
            case "users"    -> btnUsers;
            case "auctions" -> btnAuctions;
            case "settings" -> btnSettings;
            default         -> btnOverview;
        };
        if (active != null && !active.getStyleClass().contains("sidebar-btn-active"))
            active.getStyleClass().add("sidebar-btn-active");
    }

    // ─── Logout ──────────────────────────────────────────────────────────────

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            cleanupListeners();
            UserSession.getInstance().logout();
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupListeners() {
        List.of("USER_LIST", "ADMIN_STATS", "AUCTION_LIST",
                        "ADMIN_CANCEL_AUCTION_SUCCESS", "ADMIN_CANCEL_AUCTION_FAILED",
                        "ADMIN_BALANCE_UPDATED", "ADMIN_BALANCE_FAILED", "ERROR")
                .forEach(ClientMain::unregisterListener);
    }

    // ─── Dialog builder helpers ──────────────────────────────────────────────

    private Label buildIconLabel(String emoji) {
        Label lbl = new Label(emoji);
        lbl.setStyle("-fx-font-size: 34px; -fx-background-color: linear-gradient(to bottom right,#DBEAFE,#EDE9FE);"
                + "-fx-background-radius:999; -fx-min-width:68; -fx-min-height:68; -fx-alignment:center;");
        return lbl;
    }

    private Label buildBoldLabel(String text, String size, String color) {
        Label lbl = new Label(text);
        lbl.setStyle(buildBoldStyle(size, color));
        return lbl;
    }

    private String buildBoldStyle(String size, String color) {
        return "-fx-font-size:" + size + "; -fx-font-weight:900; -fx-text-fill:" + color + ";";
    }

    private Label buildSubLabel(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-size:13px; -fx-text-fill:#64748B;");
        return lbl;
    }

    private Label buildBadge(String text, String textColor, String bgColor) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:11px; -fx-font-weight:900; -fx-text-fill:" + textColor
                + "; -fx-background-color:" + bgColor + "; -fx-background-radius:999; -fx-padding:5 10;");
        return lbl;
    }

    private VBox balanceCard(String titleText, Label valueLabel, String bg, String border) {
        Label title = new Label(titleText);
        title.setStyle(STYLE_CARD_LABEL);
        VBox card = new VBox(5, title, valueLabel);
        card.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:18;"
                + "-fx-border-color:" + border + "; -fx-border-radius:18; -fx-padding:16;");
        return card;
    }

    private VBox styledCard(String bg, String border, VBox inner) {
        inner.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:18;"
                + "-fx-border-color:" + border + "; -fx-border-radius:18; -fx-padding:16;");
        return inner;
    }

    private HBox stretchHBox(int spacing, VBox... nodes) {
        HBox box = new HBox(spacing, nodes);
        for (VBox n : nodes) {
            HBox.setHgrow(n, Priority.ALWAYS);
            n.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    private TextField styledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefHeight(42);
        tf.setStyle("-fx-background-color:white; -fx-background-radius:12;"
                + "-fx-border-color:#CBD5E1; -fx-border-radius:12;"
                + "-fx-padding:0 14; -fx-font-size:14px; -fx-font-weight:700;");
        return tf;
    }

    private void styleDialogButton(Button btn, String text, String bg, String fg) {
        if (btn == null) return;
        btn.setText(text);
        btn.setStyle("-fx-background-color:" + bg + "; -fx-text-fill:" + fg
                + "; -fx-font-weight:900; -fx-background-radius:12; -fx-padding:10 22; -fx-cursor:hand;");
    }

    // ─── Data helpers ────────────────────────────────────────────────────────

    private String extractMessage(String payload, String fallback) {
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> m = gson.fromJson(payload, type);
            return String.valueOf(m.getOrDefault("message", fallback));
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Parse số tiền nhập từ TextField — hỗ trợ nhiều format (dấu chấm/phẩy, ký hiệu đ/VND).
     */
    private double parseMoneyInput(String text) {
        if (text == null || text.trim().isEmpty()) throw new NumberFormatException("empty");
        String raw = text.trim()
                .replace("đ", "").replace("VNĐ", "").replace("VND", "").replace(" ", "");
        if (raw.matches("-?\\d{1,3}(\\.\\d{3})+(,\\d+)?"))
            raw = raw.replace(".", "").replace(",", ".");
        else if (raw.matches("-?\\d{1,3}(,\\d{3})+(\\.\\d+)?"))
            raw = raw.replace(",", "");
        else if (raw.contains(",") && !raw.contains("."))
            raw = raw.replace(",", ".");
        raw = raw.replaceAll("[^0-9.\\-]", "");
        if (raw.isBlank() || raw.equals("-")) throw new NumberFormatException("invalid");
        return Double.parseDouble(raw);
    }

    private int getInt(Map<String, Object> map, String key) {
        return (int) Math.round(getDouble(map, key));
    }

    private double getDouble(Map<String, Object> map, String key) {
        if (map == null) return 0;
        return UiUtils.numberFrom(map.get(key), 0);
    }
}