package client.controllers;

import client.models.user.UserViewModel;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.lang.reflect.Type;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.scene.layout.VBox;

public class AdminDashboardController implements Initializable {

    @FXML private Label lblPageTitle;
    @FXML private Label lblTableTitle;

    @FXML private Button btnOverview;
    @FXML private Button btnUsers;
    @FXML private Button btnAuctions;
    @FXML private Button btnSettings;

    @FXML private Label lblTotalUsers;
    @FXML private Label lblTotalItems;
    @FXML private Label lblRevenue;

    @FXML private TextField txtSearchUser;
    @FXML private ComboBox<String> cmbRoleFilter;

    @FXML private TableView<UserViewModel> tableUsers;
    @FXML private TableColumn<UserViewModel, Integer> colId;
    @FXML private TableColumn<UserViewModel, String> colUsername;
    @FXML private TableColumn<UserViewModel, String> colRole;
    @FXML private TableColumn<UserViewModel, String> colStatus;

    @FXML private TableView<Map<String, Object>> tableAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionId;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionItem;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionPrice;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionWinner;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionStatus;

    @FXML private javafx.scene.layout.VBox settingsPane;
    @FXML private TextField txtPlatformFee;
    @FXML private TextField txtDepositLimit;
    @FXML private TextField txtDefaultDuration;
    @FXML private Label lblSettingMessage;

    private final Gson gson = new Gson();
    private final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private List<UserViewModel> cachedUsers = new ArrayList<>();
    private List<Map<String, Object>> cachedAuctions = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupUserTable();
        setupAuctionTable();
        setupDefaultSettings();
        registerListeners();

        showOverview(null);
        // [Fix Lag] Defer network call sang sau khi UI render xong
        Platform.runLater(this::loadData);
    }

    private void setupUserTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        cmbRoleFilter.setValue("TẤT CẢ");
    }

    private void setupAuctionTable() {
        colAuctionId.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(getInt(data.getValue(), "id"))));

        colAuctionItem.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("itemName", "N/A"))));

        colAuctionPrice.setCellValueFactory(data -> {
            long price = getLong(data.getValue(), "currentPrice");
            return new SimpleStringProperty(VND.format(price) + " đ");
        });

        colAuctionWinner.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getOrDefault("currentWinner", "Chưa có"))));

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
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );

            return row;
        });
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
                cachedUsers = serverList;
                applyUserFilter();

                if (lblTotalUsers != null) {
                    lblTotalUsers.setText(VND.format(cachedUsers.size()));
                }
            });
        });

        ClientMain.registerListener("ADMIN_STATS", payload -> {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> stats = gson.fromJson(payload, type);

            Platform.runLater(() -> {
                int totalUsers = getInt(stats, "totalUsers");
                int totalItems = getInt(stats, "totalItems");
                long revenue = getLong(stats, "revenue");

                lblTotalUsers.setText(VND.format(totalUsers));
                lblTotalItems.setText(VND.format(totalItems));
                lblRevenue.setText(VND.format(revenue) + " đ");
            });
        });

        ClientMain.registerListener("AUCTION_LIST", payload -> {
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> list = gson.fromJson(payload, type);

            Platform.runLater(() -> {
                cachedAuctions = list;
                tableAuctions.setItems(FXCollections.observableArrayList(cachedAuctions));
            });
        });

        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_SUCCESS", payload -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.INFORMATION, "Thành công", payload);
                loadData();
            });
        });

        ClientMain.registerListener("ADMIN_CANCEL_AUCTION_FAILED", payload -> {
            Platform.runLater(() ->
                    showAlert(Alert.AlertType.ERROR, "Lỗi", payload)
            );
        });
    }

    private void loadData() {
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_USERS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ADMIN_STATS", "")));
        ClientMain.send(gson.toJson(new MessageDTO("GET_ALL_AUCTIONS", "")));
    }

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
                    || user.getUsername().toLowerCase().contains(keyword);

            boolean matchRole = "TẤT CẢ".equals(role)
                    || role.equalsIgnoreCase(user.getRole());

            if (matchKeyword && matchRole) {
                filtered.add(user);
            }
        }

        tableUsers.setItems(FXCollections.observableArrayList(filtered));
    }

    private void cancelSelectedAuction(Map<String, Object> selected) {
        if (selected == null) return;

        int auctionId = getInt(selected, "id");
        String status = String.valueOf(selected.getOrDefault("status", ""));

        if ("PAID".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) {
            showAlert(Alert.AlertType.WARNING,
                    "Không thể hủy",
                    "Phiên đã kết thúc hoặc đã thanh toán, không nên hủy.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy phiên");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn hủy phiên đấu giá #" + auctionId + " không?");

        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                ClientMain.send(gson.toJson(
                        new MessageDTO("ADMIN_CANCEL_AUCTION", String.valueOf(auctionId))
                ));
            }
        });
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

        tableUsers.setVisible(false);
        tableUsers.setManaged(false);

        tableAuctions.setVisible(true);
        tableAuctions.setManaged(true);

        settingsPane.setVisible(false);
        settingsPane.setManaged(false);

        tableAuctions.setItems(FXCollections.observableArrayList(cachedAuctions));

        updateSidebar("auctions");
    }

    @FXML
    void showSettings(ActionEvent event) {
        lblPageTitle.setText("Cài đặt hệ thống");
        lblTableTitle.setText("Thiết lập hệ thống");

        tableUsers.setVisible(false);
        tableUsers.setManaged(false);

        tableAuctions.setVisible(false);
        tableAuctions.setManaged(false);

        settingsPane.setVisible(true);
        settingsPane.setManaged(true);

        updateSidebar("settings");
    }

    private void showUserTable() {
        tableUsers.setVisible(true);
        tableUsers.setManaged(true);

        tableAuctions.setVisible(false);
        tableAuctions.setManaged(false);

        settingsPane.setVisible(false);
        settingsPane.setManaged(false);

        applyUserFilter();
    }

    @FXML
    void handleSaveSettings(ActionEvent event) {
        try {
            double fee = Double.parseDouble(txtPlatformFee.getText().trim());
            long depositLimit = Long.parseLong(txtDepositLimit.getText().trim());
            int duration = Integer.parseInt(txtDefaultDuration.getText().trim());

            if (fee < 0 || fee > 20) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Phí sàn phải từ 0 đến 20%.");
                return;
            }

            if (depositLimit <= 0) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Giới hạn nạp tiền phải lớn hơn 0.");
                return;
            }

            if (duration <= 0) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Thời gian đấu giá phải lớn hơn 0 phút.");
                return;
            }

            lblSettingMessage.setText(
                    "Đã lưu: phí sàn " + fee + "%, giới hạn nạp "
                            + VND.format(depositLimit) + " đ, thời gian mặc định "
                            + duration + " phút."
            );
            lblSettingMessage.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");

            showAlert(Alert.AlertType.INFORMATION, "Lưu thành công", "Cài đặt hệ thống đã được lưu cho phiên làm việc hiện tại.");
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Sai định dạng",
                    "Vui lòng chỉ nhập số. Không nhập chữ hoặc ký tự đặc biệt.");
        }
    }

    @FXML
    void handleResetSettings(ActionEvent event) {
        setupDefaultSettings();
        lblSettingMessage.setText("Đã đặt lại cấu hình mặc định.");
        lblSettingMessage.setStyle("-fx-text-fill: #64748b;");
    }

    private void updateSidebar(String tab) {
        btnOverview.getStyleClass().remove("sidebar-btn-active");
        btnUsers.getStyleClass().remove("sidebar-btn-active");
        btnAuctions.getStyleClass().remove("sidebar-btn-active");
        btnSettings.getStyleClass().remove("sidebar-btn-active");

        if ("overview".equals(tab)) btnOverview.getStyleClass().add("sidebar-btn-active");
        if ("users".equals(tab)) btnUsers.getStyleClass().add("sidebar-btn-active");
        if ("auctions".equals(tab)) btnAuctions.getStyleClass().add("sidebar-btn-active");
        if ("settings".equals(tab)) btnSettings.getStyleClass().add("sidebar-btn-active");
    }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            ClientMain.unregisterListener("USER_LIST");
            ClientMain.unregisterListener("ADMIN_STATS");
            ClientMain.unregisterListener("AUCTION_LIST");
            ClientMain.unregisterListener("ADMIN_CANCEL_AUCTION_SUCCESS");
            ClientMain.unregisterListener("ADMIN_CANCEL_AUCTION_FAILED");

            Parent root = FXMLLoader.load(getClass().getResource("/client/views/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        return ((Number) value).intValue();
    }

    private long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        return ((Number) value).longValue();
    }
}