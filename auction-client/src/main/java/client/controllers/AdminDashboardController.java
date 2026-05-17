package client.controllers;

import java.lang.reflect.Type;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import client.models.user.UserSession;
import client.models.user.UserViewModel;
import client.networks.ClientMain;
import client.networks.MessageDTO;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * AdminDashboardController — Điều khiển màn hình quản trị.
 *
 * <p>Sau khi loại bỏ tính năng nạp tiền và báo cáo doanh thu, dashboard chỉ còn 4 tab:
 *
 * <ul>
 *   <li>Tổng quan — Hiển thị số liệu cơ bản và danh sách user.
 *   <li>Người dùng — Quản lý tài khoản, điều chỉnh ví.
 *   <li>Phiên đấu giá — Kiểm duyệt và hủy phiên.
 *   <li>Cài đặt — Thiết lập phí sàn, giới hạn nạp, thời lượng mặc định.
 * </ul>
 */
public class AdminDashboardController implements Initializable {

    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    // ─── Top bar ──────────────────────────────────────────────────────────────
    @FXML private Label lblPageTitle;
    @FXML private Label lblTableTitle;

    // ─── Sidebar ──────────────────────────────────────────────────────────────
    @FXML private Button btnOverview;
    @FXML private Button btnUsers;
    @FXML private Button btnAuctions;
    @FXML private Button btnSettings;

    // ─── Stat cards ───────────────────────────────────────────────────────────
    @FXML private Label lblTotalUsers;
    @FXML private Label lblTotalItems;

    // ─── User filter & action bar ─────────────────────────────────────────────
    @FXML private HBox filterBar;
    @FXML private HBox userActionBar;
    @FXML private HBox auctionActionBar;
    @FXML private TextField txtSearchUser;
    @FXML private ComboBox<String> cmbRoleFilter;

    // ─── Users table ──────────────────────────────────────────────────────────
    @FXML private TableView<UserViewModel> tableUsers;
    @FXML private TableColumn<UserViewModel, Integer> colId;
    @FXML private TableColumn<UserViewModel, String> colUsername;
    @FXML private TableColumn<UserViewModel, String> colRole;
    @FXML private TableColumn<UserViewModel, String> colBalance;
    @FXML private TableColumn<UserViewModel, String> colStatus;

    // ─── Auctions table ───────────────────────────────────────────────────────
    @FXML private TableView<Map<String, Object>> tableAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionId;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionItem;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionPrice;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionWinner;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionStatus;

    // ─── Settings pane ────────────────────────────────────────────────────────
    @FXML private VBox settingsPane;
    @FXML private TextField txtPlatformFee;
    @FXML private TextField txtDepositLimit;
    @FXML private TextField txtDefaultDuration;
    @FXML private Label lblSettingMessage;

    private final Gson gson = new Gson();

    private List<UserViewModel> cachedUsers = new ArrayList<>();
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

    // ─── Setup tables ────────────────────────────────────────────────────────

    private void setupUserTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colBalance.setCellValueFactory(data ->
                new SimpleStringProperty(formatMoney(data.getValue().getBalance())));

        cmbRoleFilter.getItems().setAll("TẤT CẢ", "ADMIN", "SELLER", "BIDDER");
        cmbRoleFilter.setValue("TẤT CẢ");

        tableUsers.setRowFactory(tv -> {
            TableRow<UserViewModel> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem adjust = new MenuItem("💳 Điều chỉnh ví");
            adjust.setOnAction(e -> handleAdjustSelectedUserBalance(null));
            menu.getItems().add(adjust);
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
                new SimpleStringProperty(formatMoney(getDouble(data.getValue(), "currentPrice"))));
        colAuctionWinner.setCellValueFactory(data ->
                new SimpleStringProperty(
                        String.valueOf(data.getValue().getOrDefault("currentWinner", "Chưa có"))));
        colAuctionStatus.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("status", "N/A"))));

        tableAuctions.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem cancelItem = new MenuItem("🚫 Hủy phiên đấu giá");
            cancelItem.setOnAction(e -> cancelSelectedAuction(row.getItem()));
            MenuItem refreshItem = new MenuItem("🔄 Tải lại dữ liệu");
            refreshItem.setOnAction(e -> loadData());
            menu.getItems().addAll(cancelItem, refreshItem);
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

    // ─── Network listeners ───────────────────────────────────────────────────

    private void registerListeners() {
        ClientMain.registerListener("USER_LIST", payload -> {
            Type listType = new TypeToken<List<UserViewModel>>() {}.getType();
            List<UserViewModel> list = gson.fromJson(payload, listType);
            Platform.runLater(() -> {
                cachedUsers = list == null ? new ArrayList<>() : list;
                applyUserFilter();
                lblTotalUsers.setText(VND.format(cachedUsers.size()));
            });
        });

        ClientMain.registerListener("ADMIN_STATS", payload -> {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> stats = gson.fromJson(payload, type);
            Platform.runLater(() -> {
                lblTotalUsers.setText(VND.format(getDouble(stats, "totalUsers")));
                lblTotalItems.setText(VND.format(getDouble(stats, "totalItems")));
            });
        });

        ClientMain.registerListener("AUCTION_LIST", payload -> {
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> list = gson.fromJson(payload, type);
            Platform.runLater(() -> {
                cachedAuctions = list == null ? new ArrayList<>() : list;
                tableAuctions.setItems(FXCollections.observableArrayList(cachedAuctions));
            });
        });

        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_SUCCESS", payload ->
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", payload);
                    loadData();
                }));
        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_FAILED", payload ->
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Lỗi", payload)));

        ClientMain.registerListener("ADMIN_BALANCE_UPDATED", payload ->
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Đã cập nhật",
                            getMessage(payload, "Đã điều chỉnh số dư."));
                    loadData();
                }));
        ClientMain.registerListener("ADMIN_BALANCE_FAILED", payload ->
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Không thể điều chỉnh ví", payload)));
        ClientMain.registerListener("ERROR", payload ->
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Lỗi", payload)));
    }

    private void loadData() {
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_USERS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ADMIN_STATS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_AUCTIONS", "")));
    }

    // ─── Tab navigation ──────────────────────────────────────────────────────

    @FXML
    void handleReload(ActionEvent event) {
        loadData();
    }

    @FXML
    void handleSearchUser() {
        applyUserFilter();
    }

    @FXML
    void handleClearUserFilter(ActionEvent event) {
        txtSearchUser.clear();
        cmbRoleFilter.setValue("TẤT CẢ");
        applyUserFilter();
    }

    private void applyUserFilter() {
        String keyword = txtSearchUser == null ? "" : txtSearchUser.getText().trim().toLowerCase();
        String role = cmbRoleFilter == null || cmbRoleFilter.getValue() == null
                ? "TẤT CẢ"
                : cmbRoleFilter.getValue();

        List<UserViewModel> filtered = new ArrayList<>();
        for (UserViewModel user : cachedUsers) {
            boolean matchKeyword = keyword.isEmpty()
                    || user.getUsername().toLowerCase().contains(keyword)
                    || String.valueOf(user.getId()).contains(keyword);
            boolean matchRole = "TẤT CẢ".equals(role) || role.equalsIgnoreCase(user.getRole());
            if (matchKeyword && matchRole) {
                filtered.add(user);
            }
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
        setVisible(tableUsers, false);
        setVisible(tableAuctions, false);
        setVisible(filterBar, false);
        setVisible(userActionBar, false);
        setVisible(auctionActionBar, false);
        setVisible(settingsPane, true);
    }

    private void setVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // ─── User & Auction actions ──────────────────────────────────────────────

    @FXML
    void handleAdjustSelectedUserBalance(ActionEvent event) {
        UserViewModel selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn người dùng",
                    "Hãy chọn người dùng cần điều chỉnh ví.");
            return;
        }

        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Điều chỉnh ví người dùng");
        dialog.setHeaderText("User #" + selected.getId() + " - " + selected.getUsername()
                + "\nSố dư hiện tại: " + formatMoney(selected.getBalance()));

        ButtonType ok = new ButtonType("Lưu điều chỉnh", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField txtDelta = new TextField();
        txtDelta.setPromptText("VD: 1000000 hoặc -500000");
        TextArea txtReason = new TextArea();
        txtReason.setPromptText("Lý do điều chỉnh...");
        txtReason.setPrefRowCount(3);

        VBox box = new VBox(10,
                new Label("Số tiền cộng/trừ"), txtDelta,
                new Label("Lý do"), txtReason);
        box.setPrefWidth(440);
        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(button -> {
            if (button != ok) {
                return null;
            }
            try {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("userId", selected.getId());
                data.put("delta",  Double.parseDouble(txtDelta.getText().trim()));
                data.put("reason", txtReason.getText() == null ? "" : txtReason.getText().trim());
                return data;
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Sai định dạng", "Số tiền phải là một số hợp lệ.");
                return null;
            }
        });

        dialog.showAndWait().ifPresent(data ->
                ClientMain.send(gson.toJson(new MessageDTO("ADMIN_ADJUST_BALANCE", gson.toJson(data)))));
    }

    @FXML
    void handleCancelSelectedAuction(ActionEvent event) {
        cancelSelectedAuction(tableAuctions.getSelectionModel().getSelectedItem());
    }

    private void cancelSelectedAuction(Map<String, Object> selected) {
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn phiên",
                    "Hãy chọn phiên đấu giá cần hủy.");
            return;
        }

        int auctionId = getInt(selected, "id");
        String status = String.valueOf(selected.getOrDefault("status", ""));
        if ("PAID".equalsIgnoreCase(status)) {
            showAlert(Alert.AlertType.WARNING, "Không thể hủy",
                    "Phiên đã thanh toán, không thể hủy.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy phiên");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn hủy phiên đấu giá #" + auctionId + " không?");
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                ClientMain.send(gson.toJson(
                        new MessageDTO("ADMIN_CANCEL_AUCTION", String.valueOf(auctionId))));
            }
        });
    }

    // ─── Settings ────────────────────────────────────────────────────────────

    @FXML
    void handleSaveSettings(ActionEvent event) {
        try {
            double fee = Double.parseDouble(txtPlatformFee.getText().trim());
            long depositLimit = Long.parseLong(txtDepositLimit.getText().trim());
            int duration = Integer.parseInt(txtDefaultDuration.getText().trim());

            if (fee < 0 || fee > 20) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                        "Phí sàn phải từ 0 đến 20%.");
                return;
            }
            if (depositLimit <= 0) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                        "Giới hạn nạp tiền phải lớn hơn 0.");
                return;
            }
            if (duration <= 0) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                        "Thời gian đấu giá phải lớn hơn 0 phút.");
                return;
            }

            lblSettingMessage.setText("Đã lưu: phí sàn " + fee + "%, giới hạn nạp "
                    + VND.format(depositLimit) + " đ, thời gian mặc định " + duration + " phút.");
            lblSettingMessage.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            showAlert(Alert.AlertType.INFORMATION, "Lưu thành công",
                    "Cài đặt hệ thống đã được lưu cho phiên làm việc hiện tại.");

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Sai định dạng",
                    "Vui lòng chỉ nhập số. Không nhập chữ hoặc ký tự đặc biệt.");
        }
    }

    @FXML
    void handleResetSettings(ActionEvent event) {
        setupDefaultSettings();
        lblSettingMessage.setText("Đã đặt lại cấu hình mặc định.");
        lblSettingMessage.setStyle("-fx-text-fill: #64748b;");
    }

    // ─── Sidebar styling ─────────────────────────────────────────────────────

    private void updateSidebar(String tab) {
        for (Button btn : new Button[] {btnOverview, btnUsers, btnAuctions, btnSettings}) {
            if (btn == null) {
                continue;
            }
            btn.getStyleClass().remove("sidebar-btn-active");
            if (!btn.getStyleClass().contains("sidebar-btn")) {
                btn.getStyleClass().add("sidebar-btn");
            }
        }

        Button active = switch (tab) {
            case "users"    -> btnUsers;
            case "auctions" -> btnAuctions;
            case "settings" -> btnSettings;
            default         -> btnOverview;
        };

        if (active != null && !active.getStyleClass().contains("sidebar-btn-active")) {
            active.getStyleClass().add("sidebar-btn-active");
        }
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
        for (String action : List.of(
                "USER_LIST", "ADMIN_STATS", "AUCTION_LIST",
                "ADMIN_CANCEL_AUCTION_SUCCESS", "ADMIN_CANCEL_AUCTION_FAILED",
                "ADMIN_BALANCE_UPDATED", "ADMIN_BALANCE_FAILED", "ERROR")) {
            ClientMain.unregisterListener(action);
        }
    }

    // ─── Utility helpers ─────────────────────────────────────────────────────

    private String getMessage(String payload, String fallback) {
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> m = gson.fromJson(payload, type);
            return String.valueOf(m.getOrDefault("message", fallback));
        } catch (Exception e) {
            return fallback;
        }
    }

    private String formatMoney(double value) {
        return VND.format(Math.round(value)) + " đ";
    }

    private int getInt(Map<String, Object> map, String key) {
        return (int) Math.round(getDouble(map, key));
    }

    private double getDouble(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}