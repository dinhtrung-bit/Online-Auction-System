package client.controllers;

import client.models.user.UserSession;
import client.models.user.UserViewModel;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.lang.reflect.Type;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;

public class AdminDashboardController implements Initializable {

    @FXML private Label lblPageTitle;
    @FXML private Label lblTableTitle;
    @FXML private HBox filterBar;
    @FXML private HBox userActionBar;
    @FXML private HBox auctionActionBar;
    @FXML private HBox depositActionBar;

    @FXML private Button btnOverview;
    @FXML private Button btnUsers;
    @FXML private Button btnAuctions;
    @FXML private Button btnDeposits;
    @FXML private Button btnReports;
    @FXML private Button btnSettings;

    @FXML private Label lblTotalUsers;
    @FXML private Label lblTotalItems;
    @FXML private Label lblRevenue;
    @FXML private Label lblPendingDeposits;

    @FXML private TextField txtSearchUser;
    @FXML private ComboBox<String> cmbRoleFilter;

    @FXML private TableView<UserViewModel> tableUsers;
    @FXML private TableColumn<UserViewModel, Integer> colId;
    @FXML private TableColumn<UserViewModel, String> colUsername;
    @FXML private TableColumn<UserViewModel, String> colRole;
    @FXML private TableColumn<UserViewModel, String> colStatus;
    @FXML private TableColumn<UserViewModel, String> colBalance;

    @FXML private TableView<Map<String, Object>> tableAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionId;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionItem;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionPrice;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionWinner;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionStatus;

    @FXML private TableView<Map<String, Object>> tableDeposits;
    @FXML private TableColumn<Map<String, Object>, String> colDepositId;
    @FXML private TableColumn<Map<String, Object>, String> colDepositUser;
    @FXML private TableColumn<Map<String, Object>, String> colDepositAmount;
    @FXML private TableColumn<Map<String, Object>, String> colDepositStatus;
    @FXML private TableColumn<Map<String, Object>, String> colDepositCreated;
    @FXML private TableColumn<Map<String, Object>, String> colDepositNote;

    @FXML private VBox revenuePane;
    @FXML private Label lblGrossSales;
    @FXML private Label lblPlatformRevenue;
    @FXML private Label lblSellerPayout;
    @FXML private Label lblPaidCount;
    @FXML private Label lblDepositApproved;
    @FXML private Label lblDepositPending;
    @FXML private Label lblDepositRejected;
    @FXML private TableView<Map<String, Object>> tableRevenue;
    @FXML private TableColumn<Map<String, Object>, String> colRevenueAuction;
    @FXML private TableColumn<Map<String, Object>, String> colRevenueItem;
    @FXML private TableColumn<Map<String, Object>, String> colRevenueFinal;
    @FXML private TableColumn<Map<String, Object>, String> colRevenueFee;
    @FXML private TableColumn<Map<String, Object>, String> colRevenueSeller;
    @FXML private TableColumn<Map<String, Object>, String> colRevenueStatus;

    @FXML private VBox settingsPane;
    @FXML private TextField txtPlatformFee;
    @FXML private TextField txtDepositLimit;
    @FXML private TextField txtDefaultDuration;
    @FXML private Label lblSettingMessage;

    private final Gson gson = new Gson();
    private final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private List<UserViewModel> cachedUsers = new ArrayList<>();
    private List<Map<String, Object>> cachedAuctions = new ArrayList<>();
    private List<Map<String, Object>> cachedDeposits = new ArrayList<>();
    private List<Map<String, Object>> cachedRevenueRows = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupUserTable();
        setupAuctionTable();
        setupDepositTable();
        setupRevenueTable();
        setupDefaultSettings();
        registerListeners();
        showOverview(null);
        Platform.runLater(this::loadData);
    }

    private void setupUserTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        if (colBalance != null) {
            colBalance.setCellValueFactory(data -> new SimpleStringProperty(formatMoney(data.getValue().getBalance())));
        }
        if (cmbRoleFilter != null) {
            cmbRoleFilter.getItems().setAll("TẤT CẢ", "ADMIN", "SELLER", "BIDDER");
            cmbRoleFilter.setValue("TẤT CẢ");
        }
        tableUsers.setRowFactory(tv -> {
            TableRow<UserViewModel> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem adjust = new MenuItem("💳 Điều chỉnh ví");
            adjust.setOnAction(e -> handleAdjustSelectedUserBalance(null));
            menu.getItems().add(adjust);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private void setupAuctionTable() {
        colAuctionId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(getInt(data.getValue(), "id"))));
        colAuctionItem.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("itemName", "N/A"))));
        colAuctionPrice.setCellValueFactory(data -> new SimpleStringProperty(formatMoney(getDouble(data.getValue(), "currentPrice"))));
        colAuctionWinner.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("currentWinner", "Chưa có"))));
        colAuctionStatus.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("status", "N/A"))));
        tableAuctions.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem cancelItem = new MenuItem("🚫 Hủy phiên đấu giá");
            cancelItem.setOnAction(e -> cancelSelectedAuction(row.getItem()));
            MenuItem refreshItem = new MenuItem("🔄 Tải lại dữ liệu");
            refreshItem.setOnAction(e -> loadData());
            menu.getItems().addAll(cancelItem, refreshItem);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private void setupDepositTable() {
        colDepositId.setCellValueFactory(data -> new SimpleStringProperty("#" + getInt(data.getValue(), "requestId")));
        colDepositUser.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("username", "N/A"))));
        colDepositAmount.setCellValueFactory(data -> new SimpleStringProperty(formatMoney(getDouble(data.getValue(), "amount"))));
        colDepositStatus.setCellValueFactory(data -> new SimpleStringProperty(statusVN(String.valueOf(data.getValue().getOrDefault("status", "")))));
        colDepositCreated.setCellValueFactory(data -> new SimpleStringProperty(shortDate(String.valueOf(data.getValue().getOrDefault("createdAt", "")))));
        colDepositNote.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("note", ""))));
        tableDeposits.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem approve = new MenuItem("✅ Duyệt yêu cầu");
            approve.setOnAction(e -> reviewSelectedDeposit(true, row.getItem()));
            MenuItem reject = new MenuItem("❌ Từ chối yêu cầu");
            reject.setOnAction(e -> reviewSelectedDeposit(false, row.getItem()));
            menu.getItems().addAll(approve, reject);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private void setupRevenueTable() {
        colRevenueAuction.setCellValueFactory(data -> new SimpleStringProperty("#" + getInt(data.getValue(), "auctionId")));
        colRevenueItem.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("itemName", "N/A"))));
        colRevenueFinal.setCellValueFactory(data -> new SimpleStringProperty(formatMoney(getDouble(data.getValue(), "finalPrice"))));
        colRevenueFee.setCellValueFactory(data -> new SimpleStringProperty(formatMoney(getDouble(data.getValue(), "platformFee"))));
        colRevenueSeller.setCellValueFactory(data -> new SimpleStringProperty(formatMoney(getDouble(data.getValue(), "sellerPayout"))));
        colRevenueStatus.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("status", "N/A"))));
    }

    private void setupDefaultSettings() {
        txtPlatformFee.setText("5");
        txtDepositLimit.setText("100000000");
        txtDefaultDuration.setText("30");
    }

    private void registerListeners() {
        ClientMain.registerListener("USER_LIST", payload -> {
            Type listType = new TypeToken<List<UserViewModel>>() {}.getType();
            List<UserViewModel> serverList = gson.fromJson(payload, listType);
            Platform.runLater(() -> {
                cachedUsers = serverList == null ? new ArrayList<>() : serverList;
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
                lblRevenue.setText(formatMoney(getDouble(stats, "revenue")));
                if (lblPendingDeposits != null) lblPendingDeposits.setText(VND.format(getDouble(stats, "pendingDeposits")));
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

        ClientMain.registerListener("DEPOSIT_REQUEST_LIST", payload -> {
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> list = gson.fromJson(payload, type);
            Platform.runLater(() -> {
                cachedDeposits = list == null ? new ArrayList<>() : list;
                tableDeposits.setItems(FXCollections.observableArrayList(cachedDeposits));
                long pending = cachedDeposits.stream().filter(m -> "PENDING".equalsIgnoreCase(String.valueOf(m.get("status")))).count();
                if (lblPendingDeposits != null) lblPendingDeposits.setText(VND.format(pending));
            });
        });

        ClientMain.registerListener("DEPOSIT_STATS", payload -> {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> stats = gson.fromJson(payload, type);
            Platform.runLater(() -> {
                if (lblDepositApproved != null) lblDepositApproved.setText(formatMoney(getDouble(stats, "approvedAmount")));
                if (lblDepositPending != null) lblDepositPending.setText(formatMoney(getDouble(stats, "pendingAmount")));
                if (lblDepositRejected != null) lblDepositRejected.setText(formatMoney(getDouble(stats, "rejectedAmount")));
            });
        });

        ClientMain.registerListener("ADMIN_REVENUE_REPORT", payload -> {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> report = gson.fromJson(payload, type);
            Platform.runLater(() -> updateRevenueReport(report));
        });

        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_SUCCESS", payload -> Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", payload);
            loadData();
        }));
        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_FAILED", payload -> Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Lỗi", payload)));

        ClientMain.registerListener("DEPOSIT_APPROVED", payload -> Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Đã duyệt", getMessage(payload, "Đã duyệt yêu cầu nạp tiền."));
            loadData();
        }));
        ClientMain.registerListener("DEPOSIT_REJECTED", payload -> Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Đã từ chối", getMessage(payload, "Đã từ chối yêu cầu nạp tiền."));
            loadData();
        }));
        ClientMain.registerListener("DEPOSIT_REVIEW_FAILED", payload -> Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Không thể xử lý", payload)));

        ClientMain.registerListener("ADMIN_BALANCE_UPDATED", payload -> Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Đã cập nhật", getMessage(payload, "Đã điều chỉnh số dư."));
            loadData();
        }));
        ClientMain.registerListener("ADMIN_BALANCE_FAILED", payload -> Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Không thể điều chỉnh ví", payload)));
        ClientMain.registerListener("ERROR", payload -> Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Lỗi", payload)));
    }

    private void loadData() {
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_USERS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ADMIN_STATS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_AUCTIONS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_DEPOSIT_REQUESTS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_DEPOSIT_STATS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ADMIN_REVENUE_REPORT", "")));
    }

    @FXML void handleReload(ActionEvent event) { loadData(); }
    @FXML void handleSearchUser() { applyUserFilter(); }

    @FXML
    void handleClearUserFilter(ActionEvent event) {
        txtSearchUser.clear();
        cmbRoleFilter.setValue("TẤT CẢ");
        applyUserFilter();
    }

    private void applyUserFilter() {
        String keyword = txtSearchUser == null ? "" : txtSearchUser.getText().trim().toLowerCase();
        String role = cmbRoleFilter == null || cmbRoleFilter.getValue() == null ? "TẤT CẢ" : cmbRoleFilter.getValue();
        List<UserViewModel> filtered = new ArrayList<>();
        for (UserViewModel user : cachedUsers) {
            boolean matchKeyword = keyword.isEmpty() || user.getUsername().toLowerCase().contains(keyword) || String.valueOf(user.getId()).contains(keyword);
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
        setVisible(depositActionBar, false);
        updateSidebar("auctions");
    }

    @FXML void showDeposits(ActionEvent event) {
        lblPageTitle.setText("Duyệt yêu cầu nạp tiền");
        lblTableTitle.setText("Yêu cầu nạp tiền của người dùng");
        showOnly(tableDeposits);
        setVisible(filterBar, false);
        setVisible(userActionBar, false);
        setVisible(auctionActionBar, false);
        setVisible(depositActionBar, true);
        updateSidebar("deposits");
    }

    @FXML void showReports(ActionEvent event) {
        lblPageTitle.setText("Báo cáo doanh thu");
        lblTableTitle.setText("Doanh thu và phí sàn chi tiết");
        showRevenuePane();
        updateSidebar("reports");
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
        setVisible(depositActionBar, false);
        applyUserFilter();
    }

    private void showOnly(TableView<?> table) {
        setVisible(tableUsers, table == tableUsers);
        setVisible(tableAuctions, table == tableAuctions);
        setVisible(tableDeposits, table == tableDeposits);
        setVisible(revenuePane, false);
        setVisible(settingsPane, false);
    }

    private void showRevenuePane() {
        setVisible(tableUsers, false); setVisible(tableAuctions, false); setVisible(tableDeposits, false);
        setVisible(filterBar, false); setVisible(userActionBar, false); setVisible(auctionActionBar, false); setVisible(depositActionBar, false);
        setVisible(revenuePane, true); setVisible(settingsPane, false);
    }

    private void showSettingsPane() {
        setVisible(tableUsers, false); setVisible(tableAuctions, false); setVisible(tableDeposits, false);
        setVisible(filterBar, false); setVisible(userActionBar, false); setVisible(auctionActionBar, false); setVisible(depositActionBar, false);
        setVisible(revenuePane, false); setVisible(settingsPane, true);
    }

    private void setVisible(Node node, boolean visible) {
        if (node == null) return;
        node.setVisible(visible);
        node.setManaged(visible);
    }

    @FXML void handleApproveDeposit(ActionEvent event) { reviewSelectedDeposit(true, tableDeposits.getSelectionModel().getSelectedItem()); }
    @FXML void handleRejectDeposit(ActionEvent event) { reviewSelectedDeposit(false, tableDeposits.getSelectionModel().getSelectedItem()); }

    private void reviewSelectedDeposit(boolean approve, Map<String, Object> selected) {
        if (selected == null) { showAlert(Alert.AlertType.WARNING, "Chưa chọn yêu cầu", "Hãy chọn một yêu cầu nạp tiền trước."); return; }
        String status = String.valueOf(selected.getOrDefault("status", ""));
        if (!"PENDING".equalsIgnoreCase(status)) {
            showAlert(Alert.AlertType.WARNING, "Đã xử lý", "Yêu cầu này không còn ở trạng thái chờ duyệt.");
            return;
        }
        int requestId = getInt(selected, "requestId");
        TextInputDialog dialog = new TextInputDialog(approve ? "Đã đối soát giao dịch" : "Thông tin giao dịch không hợp lệ");
        dialog.setTitle(approve ? "Duyệt nạp tiền" : "Từ chối nạp tiền");
        dialog.setHeaderText((approve ? "Duyệt" : "Từ chối") + " yêu cầu #" + requestId + " - " + formatMoney(getDouble(selected, "amount")));
        dialog.setContentText("Ghi chú Admin:");
        dialog.showAndWait().ifPresent(note -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", requestId);
            data.put("adminNote", note == null ? "" : note.trim());
            ClientMain.send(gson.toJson(new MessageDTO(approve ? "APPROVE_DEPOSIT" : "REJECT_DEPOSIT", gson.toJson(data))));
        });
    }

    @FXML void handleAdjustSelectedUserBalance(ActionEvent event) {
        UserViewModel selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert(Alert.AlertType.WARNING, "Chưa chọn người dùng", "Hãy chọn người dùng cần điều chỉnh ví."); return; }
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Điều chỉnh ví người dùng");
        dialog.setHeaderText("User #" + selected.getId() + " - " + selected.getUsername() + "\nSố dư hiện tại: " + formatMoney(selected.getBalance()));
        ButtonType ok = new ButtonType("Lưu điều chỉnh", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        TextField txtDelta = new TextField();
        txtDelta.setPromptText("VD: 1000000 hoặc -500000");
        TextArea txtReason = new TextArea();
        txtReason.setPromptText("Lý do điều chỉnh...");
        txtReason.setPrefRowCount(3);
        VBox box = new VBox(10, new Label("Số tiền cộng/trừ"), txtDelta, new Label("Lý do"), txtReason);
        box.setPrefWidth(440);
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(button -> {
            if (button != ok) return null;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", selected.getId());
            data.put("delta", Double.parseDouble(txtDelta.getText().trim()));
            data.put("reason", txtReason.getText() == null ? "" : txtReason.getText().trim());
            return data;
        });
        dialog.showAndWait().ifPresent(data -> ClientMain.send(gson.toJson(new MessageDTO("ADMIN_ADJUST_BALANCE", gson.toJson(data)))));
    }

    @FXML void handleCancelSelectedAuction(ActionEvent event) { cancelSelectedAuction(tableAuctions.getSelectionModel().getSelectedItem()); }

    private void cancelSelectedAuction(Map<String, Object> selected) {
        if (selected == null) { showAlert(Alert.AlertType.WARNING, "Chưa chọn phiên", "Hãy chọn phiên đấu giá cần hủy."); return; }
        int auctionId = getInt(selected, "id");
        String status = String.valueOf(selected.getOrDefault("status", ""));
        if ("PAID".equalsIgnoreCase(status)) {
            showAlert(Alert.AlertType.WARNING, "Không thể hủy", "Phiên đã thanh toán, không thể hủy.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy phiên");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn hủy phiên đấu giá #" + auctionId + " không?");
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) ClientMain.send(gson.toJson(new MessageDTO("ADMIN_CANCEL_AUCTION", String.valueOf(auctionId))));
        });
    }

    @FXML void handleSaveSettings(ActionEvent event) {
        try {
            double fee = Double.parseDouble(txtPlatformFee.getText().trim());
            long depositLimit = Long.parseLong(txtDepositLimit.getText().trim());
            int duration = Integer.parseInt(txtDefaultDuration.getText().trim());
            if (fee < 0 || fee > 20) { showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Phí sàn phải từ 0 đến 20%."); return; }
            if (depositLimit <= 0) { showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Giới hạn nạp tiền phải lớn hơn 0."); return; }
            if (duration <= 0) { showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Thời gian đấu giá phải lớn hơn 0 phút."); return; }
            lblSettingMessage.setText("Đã lưu: phí sàn " + fee + "%, giới hạn nạp " + VND.format(depositLimit) + " đ, thời gian mặc định " + duration + " phút.");
            lblSettingMessage.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            showAlert(Alert.AlertType.INFORMATION, "Lưu thành công", "Cài đặt hệ thống đã được lưu cho phiên làm việc hiện tại.");
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Sai định dạng", "Vui lòng chỉ nhập số. Không nhập chữ hoặc ký tự đặc biệt.");
        }
    }

    @FXML void handleResetSettings(ActionEvent event) {
        setupDefaultSettings();
        lblSettingMessage.setText("Đã đặt lại cấu hình mặc định.");
        lblSettingMessage.setStyle("-fx-text-fill: #64748b;");
    }

    private void updateRevenueReport(Map<String, Object> report) {
        if (report == null) report = new HashMap<>();
        lblGrossSales.setText(formatMoney(getDouble(report, "grossSales")));
        lblPlatformRevenue.setText(formatMoney(getDouble(report, "platformRevenue")));
        lblSellerPayout.setText(formatMoney(getDouble(report, "sellerPayout")));
        lblPaidCount.setText(VND.format(getDouble(report, "paidCount")) + " phiên đã thanh toán");
        Object rows = report.get("rows");
        Type rowsType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        cachedRevenueRows = rows == null ? new ArrayList<>() : gson.fromJson(gson.toJson(rows), rowsType);
        tableRevenue.setItems(FXCollections.observableArrayList(cachedRevenueRows));
    }

    private void updateSidebar(String tab) {
        for (Button btn : List.of(btnOverview, btnUsers, btnAuctions, btnDeposits, btnReports, btnSettings)) {
            if (btn == null) continue;
            btn.getStyleClass().remove("sidebar-btn-active");
            if (!btn.getStyleClass().contains("sidebar-btn")) btn.getStyleClass().add("sidebar-btn");
        }
        Button active = switch (tab) {
            case "users" -> btnUsers;
            case "auctions" -> btnAuctions;
            case "deposits" -> btnDeposits;
            case "reports" -> btnReports;
            case "settings" -> btnSettings;
            default -> btnOverview;
        };
        if (active != null && !active.getStyleClass().contains("sidebar-btn-active")) active.getStyleClass().add("sidebar-btn-active");
    }

    @FXML void handleLogout(ActionEvent event) {
        try {
            cleanupListeners();
            UserSession.getInstance().logout();
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void cleanupListeners() {
        for (String action : List.of("USER_LIST", "ADMIN_STATS", "AUCTION_LIST", "DEPOSIT_REQUEST_LIST", "DEPOSIT_STATS",
                "ADMIN_REVENUE_REPORT", "ADMIN_CANCEL_AUCTION_SUCCESS", "ADMIN_CANCEL_AUCTION_FAILED", "DEPOSIT_APPROVED",
                "DEPOSIT_REJECTED", "DEPOSIT_REVIEW_FAILED", "ADMIN_BALANCE_UPDATED", "ADMIN_BALANCE_FAILED", "ERROR")) {
            ClientMain.unregisterListener(action);
        }
    }

    private String getMessage(String payload, String fallback) {
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> m = gson.fromJson(payload, type);
            return String.valueOf(m.getOrDefault("message", fallback));
        } catch (Exception e) { return fallback; }
    }

    private String formatMoney(double value) { return VND.format(Math.round(value)) + " đ"; }
    private String shortDate(String s) { return s == null || s.isBlank() ? "" : s.replace('T', ' '); }
    private String statusVN(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "PENDING" -> "Chờ duyệt";
            case "APPROVED" -> "Đã duyệt";
            case "REJECTED" -> "Từ chối";
            default -> status;
        };
    }

    private int getInt(Map<String, Object> map, String key) { return (int) Math.round(getDouble(map, key)); }
    private double getDouble(Map<String, Object> map, String key) {
        if (map == null) return 0;
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
