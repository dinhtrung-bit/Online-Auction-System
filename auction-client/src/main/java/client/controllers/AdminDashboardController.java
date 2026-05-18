package client.controllers;

import client.models.user.UserSession;
import client.models.user.UserViewModel;
import client.services.ServerGateway;
import client.utils.MapAccessor;
import client.utils.MoneyFormatter;
import client.utils.dialogs.AdjustBalanceDialog;
import client.utils.dialogs.Dialogs;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * AdminDashboardController — Quản lý màn hình quản trị viên.
 *
 * <p>Dashboard gồm 4 tab: Tổng quan, Người dùng, Phiên đấu giá, Cài đặt.
 *
 * <p><b>Refactor v2:</b>
 * <ul>
 *   <li>Dialog "Điều chỉnh ví" tách hẳn sang {@link AdjustBalanceDialog}.
 *   <li>Network qua {@link ServerGateway} — không lặp lại Gson/MessageDTO.
 *   <li>Alert qua {@link Dialogs}.
 *   <li>Đọc Map<String,Object> qua {@link MapAccessor}.
 * </ul>
 */
public class AdminDashboardController implements Initializable {

    // ─── FXML — Top bar / sidebar / cards ───────────────────────────
    @FXML private Label lblPageTitle, lblTableTitle;
    @FXML private Button btnOverview, btnUsers, btnAuctions, btnSettings;
    @FXML private Label lblTotalUsers, lblTotalItems;

    // ─── FXML — User filter & action bar ────────────────────────────
    @FXML private HBox filterBar, userActionBar, auctionActionBar;
    @FXML private TextField txtSearchUser;
    @FXML private ComboBox<String> cmbRoleFilter;

    // ─── FXML — Users table ────────────────────────────────────────
    @FXML private TableView<UserViewModel> tableUsers;
    @FXML private TableColumn<UserViewModel, Integer> colId;
    @FXML private TableColumn<UserViewModel, String>  colUsername;
    @FXML private TableColumn<UserViewModel, String>  colRole;
    @FXML private TableColumn<UserViewModel, String>  colBalance;
    @FXML private TableColumn<UserViewModel, String>  colStatus;

    // ─── FXML — Auctions table ─────────────────────────────────────
    @FXML private TableView<Map<String, Object>> tableAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionId;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionItem;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionPrice;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionWinner;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionStatus;

    // ─── FXML — Settings ────────────────────────────────────────────
    @FXML private VBox      settingsPane;
    @FXML private TextField txtPlatformFee, txtDepositLimit, txtDefaultDuration;
    @FXML private Label     lblSettingMessage;

    // ─── State ──────────────────────────────────────────────────────
    private List<UserViewModel> cachedUsers           = new ArrayList<>();
    private List<Map<String, Object>> cachedAuctions  = new ArrayList<>();

    private static final List<String> LISTENER_ACTIONS = List.of(
            "USER_LIST", "ADMIN_STATS", "AUCTION_LIST",
            "ADMIN_CANCEL_AUCTION_SUCCESS", "ADMIN_CANCEL_AUCTION_FAILED",
            "ADMIN_BALANCE_UPDATED", "ADMIN_BALANCE_FAILED", "ERROR");

    // ─── Lifecycle ──────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupUserTable();
        setupAuctionTable();
        setupDefaultSettings();
        registerListeners();
        showOverview(null);
        Platform.runLater(this::loadData);
    }

    // ─── Table setup ────────────────────────────────────────────────

    private void setupUserTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colBalance.setCellValueFactory(d ->
                new SimpleStringProperty(MoneyFormatter.format(d.getValue().getBalance())));

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
        colAuctionId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(MapAccessor.getInt(d.getValue(), "id"))));
        colAuctionItem.setCellValueFactory(d ->
                new SimpleStringProperty(MapAccessor.getString(d.getValue(), "itemName", "N/A")));
        colAuctionPrice.setCellValueFactory(d ->
                new SimpleStringProperty(MoneyFormatter.format(MapAccessor.getDouble(d.getValue(), "currentPrice"))));
        colAuctionWinner.setCellValueFactory(d ->
                new SimpleStringProperty(MapAccessor.getString(d.getValue(), "currentWinner", "Chưa có")));
        colAuctionStatus.setCellValueFactory(d ->
                new SimpleStringProperty(MapAccessor.getString(d.getValue(), "status", "N/A")));

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

    // ─── Network ────────────────────────────────────────────────────

    private void registerListeners() {
        ServerGateway.onList("USER_LIST", UserViewModel.class, list -> {
            cachedUsers = list != null ? list : new ArrayList<>();
            applyUserFilter();
            lblTotalUsers.setText(String.valueOf(cachedUsers.size()));
        });

        ServerGateway.onMap("ADMIN_STATS", stats -> {
            lblTotalUsers.setText(String.valueOf(MapAccessor.getLong(stats, "totalUsers", 0)));
            lblTotalItems.setText(String.valueOf(MapAccessor.getLong(stats, "totalItems", 0)));
        });

        ServerGateway.onMapList("AUCTION_LIST", list -> {
            cachedAuctions = list != null ? list : new ArrayList<>();
            tableAuctions.setItems(FXCollections.observableArrayList(cachedAuctions));
        });

        ServerGateway.onString("ADMIN_CANCEL_AUCTION_SUCCESS", payload -> {
            Dialogs.info("Thành công", payload);
            loadData();
        });
        ServerGateway.onString("ADMIN_CANCEL_AUCTION_FAILED", payload ->
                Dialogs.error("Lỗi", payload));

        ServerGateway.onString("ADMIN_BALANCE_UPDATED", payload -> {
            Dialogs.info("Đã cập nhật", extractMessage(payload, "Đã điều chỉnh số dư."));
            loadData();
        });
        ServerGateway.onString("ADMIN_BALANCE_FAILED", payload ->
                Dialogs.error("Không thể điều chỉnh ví", payload));

        ServerGateway.onString("ERROR", payload -> Dialogs.error("Lỗi", payload));
    }

    private void loadData() {
        ServerGateway.send("GET_ALL_USERS",    "");
        ServerGateway.send("GET_ADMIN_STATS",  "");
        ServerGateway.send("GET_ALL_AUCTIONS", "");
    }

    // ─── Tab navigation ─────────────────────────────────────────────

    @FXML void handleReload(ActionEvent event) { loadData(); }
    @FXML void handleSearchUser()               { applyUserFilter(); }

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

    @FXML void showOverview(ActionEvent event) {
        lblPageTitle.setText("Tổng quan hệ thống");
        lblTableTitle.setText("Danh sách người dùng mới nhất");
        showUserTable();
        updateSidebar("overview");
    }
    @FXML void showUsers(ActionEvent event) {
        lblPageTitle.setText("Quản lý người dùng");
        lblTableTitle.setText("Danh sách tài khoản trong hệ thống");
        showUserTable();
        updateSidebar("users");
    }
    @FXML void showAuctions(ActionEvent event) {
        lblPageTitle.setText("Kiểm duyệt phiên đấu giá");
        lblTableTitle.setText("Danh sách phiên đấu giá trong hệ thống");
        showOnly(tableAuctions);
        setVisible(filterBar, false);
        setVisible(userActionBar, false);
        setVisible(auctionActionBar, true);
        updateSidebar("auctions");
    }
    @FXML void showSettings(ActionEvent event) {
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

    // ─── User actions ───────────────────────────────────────────────

    @FXML
    void handleAdjustSelectedUserBalance(ActionEvent event) {
        UserViewModel selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.warn("Chưa chọn người dùng", "Hãy chọn người dùng cần điều chỉnh ví.");
            return;
        }
        AdjustBalanceDialog.show(selected);
    }

    // ─── Auction actions ────────────────────────────────────────────

    @FXML
    void handleCancelSelectedAuction(ActionEvent event) {
        cancelSelectedAuction(tableAuctions.getSelectionModel().getSelectedItem());
    }

    private void cancelSelectedAuction(Map<String, Object> selected) {
        if (selected == null) {
            Dialogs.warn("Chưa chọn phiên", "Hãy chọn phiên đấu giá cần hủy.");
            return;
        }
        String status = MapAccessor.getString(selected, "status", "");
        if ("PAID".equalsIgnoreCase(status)) {
            Dialogs.warn("Không thể hủy", "Phiên đã thanh toán, không thể hủy.");
            return;
        }
        int auctionId = MapAccessor.getInt(selected, "id");
        if (Dialogs.confirm("Xác nhận hủy phiên",
                "Bạn có chắc muốn hủy phiên đấu giá #" + auctionId + " không?")) {
            ServerGateway.send("ADMIN_CANCEL_AUCTION", String.valueOf(auctionId));
        }
    }

    // ─── Settings ───────────────────────────────────────────────────

    @FXML
    void handleSaveSettings(ActionEvent event) {
        try {
            double fee          = Double.parseDouble(txtPlatformFee.getText().trim());
            long   depositLimit = Long.parseLong(txtDepositLimit.getText().trim());
            int    duration     = Integer.parseInt(txtDefaultDuration.getText().trim());

            if (fee < 0 || fee > 20) {
                Dialogs.warn("Dữ liệu không hợp lệ", "Phí sàn phải từ 0 đến 20%."); return;
            }
            if (depositLimit <= 0) {
                Dialogs.warn("Dữ liệu không hợp lệ", "Giới hạn nạp tiền phải lớn hơn 0."); return;
            }
            if (duration <= 0) {
                Dialogs.warn("Dữ liệu không hợp lệ", "Thời gian đấu giá phải lớn hơn 0 phút."); return;
            }

            lblSettingMessage.setText("Đã lưu: phí sàn " + fee + "%, giới hạn nạp "
                    + MoneyFormatter.format(depositLimit) + ", thời gian mặc định " + duration + " phút.");
            lblSettingMessage.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            Dialogs.info("Lưu thành công",
                    "Cài đặt hệ thống đã được lưu cho phiên làm việc hiện tại.");
        } catch (NumberFormatException e) {
            Dialogs.error("Sai định dạng",
                    "Vui lòng chỉ nhập số. Không nhập chữ hoặc ký tự đặc biệt.");
        }
    }

    @FXML
    void handleResetSettings(ActionEvent event) {
        setupDefaultSettings();
        lblSettingMessage.setText("Đã đặt lại cấu hình mặc định.");
        lblSettingMessage.setStyle("-fx-text-fill: #64748b;");
    }

    // ─── Sidebar ────────────────────────────────────────────────────

    private void updateSidebar(String tab) {
        for (Button btn : new Button[]{btnOverview, btnUsers, btnAuctions, btnSettings}) {
            if (btn == null) continue;
            btn.getStyleClass().remove("sidebar-btn-active");
            if (!btn.getStyleClass().contains("sidebar-btn"))
                btn.getStyleClass().add("sidebar-btn");
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

    // ─── Logout ─────────────────────────────────────────────────────

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            ServerGateway.off(LISTENER_ACTIONS.toArray(String[]::new));
            UserSession.getInstance().logout();
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── Data helpers ───────────────────────────────────────────────

    private String extractMessage(String payload, String fallback) {
        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> m = new com.google.gson.Gson().fromJson(payload, type);
            return MapAccessor.getString(m, "message", fallback);
        } catch (Exception e) {
            return fallback;
        }
    }
}
