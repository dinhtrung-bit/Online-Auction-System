package client.controllers;

import client.models.item.Art;
import client.models.item.Electronics;
import client.models.item.Item;
import client.models.user.UserSession;
import client.services.RequestResponse;
import client.services.ServerGateway;
import client.utils.DateTimes;
import client.utils.MapAccessor;
import client.utils.MoneyFormatter;
import client.utils.SafeParser;
import client.utils.StatusMapper;
import client.utils.dialogs.CreateAuctionDialog;
import client.utils.dialogs.Dialogs;
import client.utils.dialogs.StyledComponents;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * SellerDashboardController — Quản lý màn hình Seller.
 *
 * <p><b>Refactor v2:</b>
 * <ul>
 *   <li>Toàn bộ form tạo phiên đấu giá tách hẳn sang
 *       {@link CreateAuctionDialog}.
 *   <li>Network qua {@link ServerGateway} + {@link RequestResponse}
 *       (CRUD sản phẩm + auction).
 *   <li>Format/parse qua {@link MoneyFormatter}, {@link DateTimes},
 *       {@link SafeParser}, {@link StatusMapper}, {@link MapAccessor}.
 *   <li>Dialog/alert qua {@link Dialogs}, {@link StyledComponents}.
 * </ul>
 */
public class SellerDashboardController {

    // ─── FXML — Inventory table ─────────────────────────────────────
    @FXML private TableView<Item> tableItems;
    @FXML private TableColumn<Item, String> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, String> colWinner;
    @FXML private TableColumn<Item, String> colStatus;

    // ─── FXML — Layout ──────────────────────────────────────────────
    @FXML private VBox inventoryView, reportView;
    @FXML private Button btnInventory, btnReport;
    @FXML private Button btnEditProduct, btnDeleteProduct, btnStartAuction, btnCancelAuction;

    // ─── FXML — Filter ──────────────────────────────────────────────
    @FXML private TextField txtProductSearch;
    @FXML private ComboBox<String> cmbProductStatusFilter;

    // ─── FXML — Header ──────────────────────────────────────────────
    @FXML private Label lblSellerName;
    @FXML private Label lblSellerItemsCount, lblSellerRunningCount, lblSellerRevenueMini;

    // ─── FXML — Report ──────────────────────────────────────────────
    @FXML private Label lblTotalItems, lblRunningAuctions, lblFinishedAuctions, lblTotalRevenue;
    @FXML private BarChart<String, Number> revenueBarChart;
    @FXML private PieChart statusPieChart;

    // ─── FXML — Selected product panel ──────────────────────────────
    @FXML private ImageView sellerProductImage;
    @FXML private VBox sellerProductImagePlaceholder;
    @FXML private Label lblSelectedProductName, lblSelectedProductId;
    @FXML private Label lblSelectedProductCategory, lblSelectedProductDescription, lblSelectedProductPrice;
    @FXML private Label lblSelectedAuctionStatus, lblSelectedAuctionPrice;
    @FXML private Label lblSelectedAuctionWinner, lblSelectedAuctionEndTime;
    @FXML private Button btnOpenAuctionRoom, btnQuickStartAuction;

    // ─── State ──────────────────────────────────────────────────────
    private ObservableList<Item> itemList;
    private final Map<String, Map<String, Object>> auctionMap = new HashMap<>();

    /**
     * Hàng đợi roomId các phiên đấu giá vừa kết thúc — chờ MY_AUCTIONS reload
     * xong để biết ai thắng và bao nhiêu rồi mới hiện thông báo cho Seller.
     */
    private final Queue<Long> pendingFinishedNotifications = new ConcurrentLinkedQueue<>();

    private static final List<String> EVENT_ACTIONS = List.of(
            "AUCTION_STARTED", "AUCTION_FINISHED", "AUCTION_CANCELED", "ERROR");

    // ─── Init ───────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        itemList = FXCollections.observableArrayList();
        tableItems.setItems(itemList);

        if (lblSellerName != null) {
            String username = UserSession.getInstance().getUsername();
            lblSellerName.setText(username == null || username.isBlank() ? "Seller" : username);
        }

        if (cmbProductStatusFilter != null) {
            cmbProductStatusFilter.getItems().setAll(
                    "TẤT CẢ", "Chưa đăng", "Sắp bắt đầu", "Đang đấu giá",
                    "Kết thúc", "Đã thanh toán", "Đã hủy");
            cmbProductStatusFilter.setValue("TẤT CẢ");
        }

        setupTablePlaceholder();
        setupTableColumns();
        setupTableSelectionAndKeys();

        updateActionButtons();
        updateSelectedProductPanel(null);

        registerLifecycleListeners();
        loadMyItemsFromServer();
    }

    private void setupTablePlaceholder() {
        Label emptyLabel = new Label("Kho hàng đang trống. Hãy thêm sản phẩm mới!");
        emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-style: italic;");
        tableItems.setPlaceholder(emptyLabel);
    }

    private void setupTableSelectionAndKeys() {
        tableItems.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            updateActionButtons();
            updateSelectedProductPanel(newItem);
        });
        tableItems.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case DELETE -> handleDeleteProduct();
                case F5     -> handleRefreshInventory();
                default     -> { }
            }
        });
        tableItems.setRowFactory(tv -> {
            TableRow<Item> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) handleEditProduct();
            });
            return row;
        });
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        colPrice.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            Map<String, Object> auction = auctionMap.get(item.getItemId());
            double price = auction != null && auction.get("currentPrice") != null
                    ? SafeParser.numberFrom(auction.get("currentPrice"), item.getStartingPrice())
                    : item.getStartingPrice();
            return new SimpleObjectProperty<>(price);
        });

        colPrice.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : MoneyFormatter.formatViaPattern(price));
            }
        });

        colWinner.setText("Thông tin chi tiết");
        colWinner.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDetails()));

        colWinner.setCellFactory(column -> new TableCell<>() {
            private final Label label = new Label();
            { label.setWrapText(true); label.setStyle("-fx-text-fill: #334155; -fx-line-spacing: 3px;"); }

            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null || text.isBlank()) { setGraphic(null); return; }
                label.setText(text);
                label.setMaxWidth(colWinner.getWidth() - 20);
                setGraphic(label);
            }
        });

        colStatus.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            String status = getRawAuctionStatus(item);
            return new SimpleStringProperty(StatusMapper.toSellerText(status));
        });

        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                Label badge = new Label(status);
                badge.setStyle(StatusMapper.sellerBadgeStyle(status));
                setGraphic(badge);
            }
        });
    }

    // ─── Lifecycle listeners (luôn-active suốt phiên) ──────────────

    private void registerLifecycleListeners() {
        ServerGateway.onString("AUCTION_STARTED", payload -> loadMyAuctionsFromServer());

        // Khi phòng của Seller kết thúc — broadcast payload là roomId.
        // Reload danh sách rồi mới show thông báo chi tiết (winner, giá cuối).
        ServerGateway.onString("AUCTION_FINISHED", payload -> {
            try { pendingFinishedNotifications.add(Long.parseLong(payload.trim())); }
            catch (Exception ignored) {}
            loadMyAuctionsFromServer();
        });

        // Khi Admin hủy phòng — reload + thông báo.
        ServerGateway.onString("AUCTION_CANCELED", payload -> {
            try {
                long roomId = Long.parseLong(payload.trim());
                Dialogs.warn("Phiên đấu giá bị hủy",
                        "⚠️ Phiên đấu giá #" + roomId + " đã bị quản trị viên hủy.");
            } catch (Exception ignored) {}
            loadMyAuctionsFromServer();
        });

        ServerGateway.onString("ERROR", payload ->
                Dialogs.error("Lỗi từ máy chủ", payload));
    }

    // ─── Load data từ server ────────────────────────────────────────

    private void loadMyItemsFromServer() {
        ServerGateway.onMapList("MY_ITEMS", list -> {
            ServerGateway.off("MY_ITEMS");
            itemList.clear();
            if (list != null) {
                for (Map<String, Object> m : list) {
                    Item item = mapToItem(m);
                    if (item != null) itemList.add(item);
                }
            }
            applyProductFilters();
            updateMiniStats();
            loadMyAuctionsFromServer();
            if (reportView != null && reportView.isVisible()) updateReport();
        });

        // [Fix Lag] defer network call sang sau khi UI render xong
        javafx.application.Platform.runLater(() ->
                ServerGateway.sendAsync("GET_MY_ITEMS", ""));
    }

    private void loadMyAuctionsFromServer() {
        ServerGateway.onMapList("MY_AUCTIONS", list -> {
            ServerGateway.off("MY_AUCTIONS");
            auctionMap.clear();
            Map<Long, Map<String, Object>> byAuctionId = new HashMap<>();

            if (list != null) {
                for (Map<String, Object> a : list) {
                    String itemId = MapAccessor.getString(a, "itemId");
                    auctionMap.put(itemId, a);
                    if (a.get("auctionId") != null) {
                        try { byAuctionId.put(MapAccessor.getLong(a, "auctionId", 0), a); }
                        catch (Exception ignored) {}
                    }
                }
            }

            applyProductFilters();
            updateMiniStats();
            updateSelectedProductPanel(tableItems.getSelectionModel().getSelectedItem());

            if (reportView != null && reportView.isVisible()) updateReport();

            // Hiện notification cho các phiên vừa kết thúc
            Long finishedId;
            while ((finishedId = pendingFinishedNotifications.poll()) != null) {
                Map<String, Object> room = byAuctionId.get(finishedId);
                if (room != null) showAuctionFinishedNotification(finishedId, room);
            }
        });
        ServerGateway.sendAsync("GET_MY_AUCTIONS", "");
    }

    /** Convert payload Map → Item (Art / Electronics). */
    private Item mapToItem(Map<String, Object> m) {
        String itemId      = MapAccessor.getString(m, "itemId");
        String name        = MapAccessor.getString(m, "name");
        String description = MapAccessor.getString(m, "description");
        String categoryInfo = MapAccessor.getString(m, "categoryInfo",
                MapAccessor.getString(m, "CategoryInfo",
                        MapAccessor.getString(m, "category", "")));
        double price = MapAccessor.getDouble(m, "startingPrice");

        Item item = categoryInfo.toUpperCase().contains("ELECT")
                ? new Electronics(itemId, name, price, 0)
                : new Art(itemId, name, price, categoryInfo);
        item.setCategory(categoryInfo.isBlank() ? "ART" : categoryInfo);
        item.setDescription(description);
        if (m.get("bidIncrement") != null)
            item.setBidIncrement(MapAccessor.getDouble(m, "bidIncrement"));
        if (m.get("imagePath") != null)
            item.setImagePath(MapAccessor.getString(m, "imagePath"));
        return item;
    }

    /**
     * Hiển thị thông báo cho Seller khi phiên đấu giá kết thúc.
     * Phân biệt 2 trường hợp: bán được (có winner) và không có ai bid.
     */
    private void showAuctionFinishedNotification(long roomId, Map<String, Object> room) {
        String itemName = MapAccessor.getString(room, "itemName", "?");
        String winner   = MapAccessor.getString(room, "currentWinner", "");
        String status   = MapAccessor.getString(room, "status", "");
        double price    = MapAccessor.getDouble(room, "currentPrice");

        if ("CANCELED".equalsIgnoreCase(status) || winner.isEmpty()) {
            Dialogs.warn("Phiên đấu giá kết thúc",
                    "😔 Phiên #" + roomId + " (" + itemName + ") đã kết thúc nhưng không có người mua.\n\n"
                            + "Bạn có thể tạo lại phiên đấu giá mới.");
        } else {
            Dialogs.info("Bán hàng thành công!",
                    "🎉 Phiên #" + roomId + " (" + itemName + ") đã kết thúc!\n\n"
                            + "Người thắng: " + winner + "\n"
                            + "Giá cuối:    " + MoneyFormatter.formatViaPattern(price));
        }
    }

    // ─── Add/Edit/Delete product ────────────────────────────────────

    @FXML
    private void showAddProductDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/add-product-dialog.fxml"));
            Parent root = loader.load();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Thêm Sản Phẩm Mới");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(root));
            dialogStage.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialogStage.showAndWait();

            AddProductController controller = loader.getController();
            Item newItem = controller.getResultItem();
            if (newItem == null) return;

            RequestResponse.exchange()
                    .request("ADD_ITEM", new com.google.gson.Gson().toJson(newItem))
                    .onSuccess(p -> {
                        Dialogs.info("Thành công", "Đã thêm sản phẩm!");
                        loadMyItemsFromServer();
                    })
                    .onFailed(p -> Dialogs.error("Lỗi", "Thêm thất bại: " + p))
                    .send();
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error("Lỗi hệ thống", "Không thể mở form thêm sản phẩm.");
        }
    }

    @FXML
    private void handleEditProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Dialogs.warn("Nhắc nhở", "Vui lòng chọn một sản phẩm để chỉnh sửa!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/add-product-dialog.fxml"));
            Parent root = loader.load();
            AddProductController controller = loader.getController();
            controller.setEditData(selectedItem);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Chỉnh sửa Sản Phẩm");
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

            Item updatedItem = controller.getResultItem();
            if (updatedItem == null) return;
            final int finalIndex = itemList.indexOf(selectedItem);

            RequestResponse.exchange()
                    .request("UPDATE_ITEM", new com.google.gson.Gson().toJson(updatedItem))
                    .onSuccess(p -> {
                        itemList.set(finalIndex, updatedItem);
                        applyProductFilters();
                        updateMiniStats();
                        Dialogs.info("Thành công", "Cập nhật sản phẩm thành công!");
                    })
                    .onFailed(p -> Dialogs.error("Cập nhật thất bại", "Server báo lỗi: " + p))
                    .send();
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error("Lỗi hệ thống", "Không thể mở form sửa sản phẩm.");
        }
    }

    @FXML void handleEditProduct(ActionEvent event) { handleEditProduct(); }

    @FXML
    private void handleDeleteProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Dialogs.warn("Nhắc nhở", "Vui lòng chọn một sản phẩm để xóa!");
            return;
        }
        if (!Dialogs.confirm("Xác nhận xóa",
                "Xóa sản phẩm: " + selectedItem.getName() + "\nBạn có chắc chắn muốn xóa?")) return;

        final Item toDelete = selectedItem;
        RequestResponse.exchange()
                .request("DELETE_ITEM", selectedItem.getItemId())
                .onSuccess(p -> {
                    itemList.remove(toDelete);
                    applyProductFilters();
                    updateMiniStats();
                    if (reportView != null && reportView.isVisible()) updateReport();
                    Dialogs.info("Thành công", "Đã xóa sản phẩm \"" + toDelete.getName() + "\".");
                })
                .onFailed(p -> Dialogs.error("Xóa thất bại", "Server báo lỗi: " + p))
                .send();
    }

    // ─── Create / Cancel auction ────────────────────────────────────

    @FXML
    private void handleStartAuction() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Dialogs.warn("Nhắc nhở", "Vui lòng chọn sản phẩm để tạo phiên đấu giá!");
            return;
        }

        Optional<String> payload = CreateAuctionDialog.show(selectedItem);
        payload.ifPresent(payloadJson ->
                RequestResponse.exchange()
                        .request("CREATE_AUCTION", payloadJson)
                        .onSuccess(p -> {
                            Dialogs.info("Thành công", "Phiên đấu giá đã được tạo thành công!");
                            loadMyAuctionsFromServer();
                        })
                        .onFailed(p -> Dialogs.error("Lỗi", "Tạo phiên thất bại: " + p))
                        .send());
    }

    @FXML
    private void handleCancelAuction() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Dialogs.warn("Nhắc nhở", "Vui lòng chọn sản phẩm có phiên đấu giá để hủy!");
            return;
        }

        Map<String, Object> auction = auctionMap.get(selectedItem.getItemId());
        if (auction == null || auction.get("auctionId") == null) {
            Dialogs.warn("Không có phiên", "Sản phẩm này chưa có phiên đấu giá nào!");
            return;
        }

        String status = MapAccessor.getString(auction, "status", "");
        if (status.equals("PAID") || status.equals("FINISHED") || status.equals("CANCELED")) {
            Dialogs.warn("Không thể hủy",
                    "Phiên đấu giá đã ở trạng thái " + StatusMapper.toSellerText(status) + " — không thể hủy.");
            return;
        }

        int auctionId;
        try { auctionId = MapAccessor.getInt(auction, "auctionId"); }
        catch (Exception e) {
            Dialogs.error("Lỗi dữ liệu", "Không đọc được ID phiên đấu giá.");
            return;
        }

        if (!Dialogs.confirm("Xác nhận hủy phiên",
                "Hủy phiên đấu giá: " + selectedItem.getName() + "\n"
                        + "Phiên #" + auctionId + " sẽ bị hủy.\n"
                        + "Lưu ý: nếu phiên đang chạy đã có người đặt giá, server sẽ từ chối.\n\n"
                        + "Bạn chắc chắn chứ?")) return;

        RequestResponse.exchange()
                .request("DELETE_AUCTION", String.valueOf(auctionId))
                .onSuccess(p -> {
                    Dialogs.info("Thành công", p);
                    loadMyAuctionsFromServer();
                })
                .onFailed(p -> Dialogs.error("Hủy phiên thất bại", "Server báo: " + p))
                .send();
    }

    // ─── Logout ─────────────────────────────────────────────────────

    @FXML
    private void handleLogout() {
        ServerGateway.off(EVENT_ACTIONS.toArray(String[]::new));
        UserSession.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/login.fxml"));
            Parent root = loader.load();
            Scene currentScene = tableItems.getScene();
            currentScene.setRoot(root);
            Stage stage = (Stage) currentScene.getWindow();
            stage.setTitle("Đăng nhập - AuctionVN");
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error("Lỗi hệ thống", "Không thể tải màn hình đăng nhập!");
        }
    }

    // ─── Inventory / Report nav ─────────────────────────────────────

    @FXML private void handleShowInventory() {
        inventoryView.setVisible(true);  inventoryView.setManaged(true);
        reportView.setVisible(false);    reportView.setManaged(false);
        setSellerNavActive(btnInventory);
    }

    @FXML private void handleShowReport() {
        inventoryView.setVisible(false); inventoryView.setManaged(false);
        reportView.setVisible(true);     reportView.setManaged(true);
        setSellerNavActive(btnReport);
        updateReport();
    }

    @FXML private void handleRefreshReport() {
        loadMyItemsFromServer();
        loadMyAuctionsFromServer();
        updateReport();
    }

    @FXML private void handleRefreshInventory() {
        loadMyItemsFromServer();
        loadMyAuctionsFromServer();
    }

    // ─── Filter / search ────────────────────────────────────────────

    @FXML private void handleProductSearch() { applyProductFilters(); }

    @FXML private void handleClearProductFilter(ActionEvent event) {
        if (txtProductSearch != null) txtProductSearch.clear();
        if (cmbProductStatusFilter != null) cmbProductStatusFilter.setValue("TẤT CẢ");
        applyProductFilters();
    }

    private void applyProductFilters() {
        if (itemList == null || tableItems == null) return;

        String keyword = txtProductSearch == null ? "" : txtProductSearch.getText().trim().toLowerCase();
        String filter = cmbProductStatusFilter == null || cmbProductStatusFilter.getValue() == null
                ? "TẤT CẢ" : cmbProductStatusFilter.getValue();

        ObservableList<Item> filtered = FXCollections.observableArrayList();
        for (Item item : itemList) {
            String statusText = StatusMapper.toSellerText(getRawAuctionStatus(item));
            boolean matchKeyword = keyword.isEmpty()
                    || SafeParser.safe(item.getItemId()).toLowerCase().contains(keyword)
                    || SafeParser.safe(item.getName()).toLowerCase().contains(keyword)
                    || SafeParser.safe(item.getDescription()).toLowerCase().contains(keyword)
                    || SafeParser.safe(item.getDetails()).toLowerCase().contains(keyword);
            boolean matchStatus = "TẤT CẢ".equals(filter)
                    || statusText.toLowerCase().contains(filter.toLowerCase());

            if (matchKeyword && matchStatus) filtered.add(item);
        }

        tableItems.setItems(filtered);
        tableItems.refresh();
        updateActionButtons();
    }

    private String getRawAuctionStatus(Item item) {
        if (item == null) return "NONE";
        Map<String, Object> auction = auctionMap.get(item.getItemId());
        return auction != null && auction.get("status") != null
                ? auction.get("status").toString() : "NONE";
    }

    // ─── Action buttons state ───────────────────────────────────────

    private void updateActionButtons() {
        Item selectedItem = tableItems == null ? null : tableItems.getSelectionModel().getSelectedItem();
        boolean hasSelection = selectedItem != null;
        String status = hasSelection ? getRawAuctionStatus(selectedItem) : "NONE";
        boolean canStart  = hasSelection && (status.equals("NONE")
                || status.equals("CANCELED") || status.equals("FINISHED") || status.equals("PAID"));
        boolean canCancel = hasSelection && (status.equals("OPEN") || status.equals("RUNNING"));

        if (btnEditProduct       != null) btnEditProduct.setDisable(!hasSelection);
        if (btnDeleteProduct     != null) btnDeleteProduct.setDisable(!hasSelection);
        if (btnStartAuction      != null) btnStartAuction.setDisable(!canStart);
        if (btnQuickStartAuction != null) btnQuickStartAuction.setDisable(!canStart);
        if (btnCancelAuction     != null) btnCancelAuction.setDisable(!canCancel);
        if (btnOpenAuctionRoom   != null) btnOpenAuctionRoom.setDisable(
                !hasSelection || auctionMap.get(selectedItem.getItemId()) == null);
    }

    // ─── Selected product panel ─────────────────────────────────────

    private void updateSelectedProductPanel(Item item) {
        if (item == null) {
            setText(lblSelectedProductName,        "Chưa chọn sản phẩm");
            setText(lblSelectedProductId,          "ID: --");
            setText(lblSelectedProductCategory,    "--");
            setText(lblSelectedProductDescription, "Chọn một sản phẩm để xem mô tả, trạng thái phiên và thao tác nhanh.");
            setText(lblSelectedProductPrice,       "--");
            setText(lblSelectedAuctionStatus,      "--");
            setText(lblSelectedAuctionPrice,       "--");
            setText(lblSelectedAuctionWinner,      "--");
            setText(lblSelectedAuctionEndTime,     "--");
            showSellerImagePlaceholder();
            return;
        }

        Map<String, Object> auction = auctionMap.get(item.getItemId());
        String category = StatusMapper.normalizeCategory(item.getCategory());
        setText(lblSelectedProductName,     item.getName());
        setText(lblSelectedProductId,       "ID: " + item.getItemId());
        setText(lblSelectedProductCategory, category);
        String desc = SafeParser.safe(item.getDescription());
        setText(lblSelectedProductDescription, desc.isBlank() ? "Chưa có mô tả." : desc);
        setText(lblSelectedProductPrice, MoneyFormatter.formatViaPattern(item.getStartingPrice()));

        if (auction != null) {
            String status   = MapAccessor.getString(auction, "status", "NONE");
            double price    = MapAccessor.getDouble(auction, "currentPrice", item.getStartingPrice());
            String winner   = MapAccessor.getString(auction, "currentWinner", "--");
            String endTime  = MapAccessor.getString(auction, "endTime", "");
            setText(lblSelectedAuctionStatus,  StatusMapper.toSellerText(status));
            setText(lblSelectedAuctionPrice,   MoneyFormatter.formatViaPattern(price));
            setText(lblSelectedAuctionWinner,  winner.isBlank() ? "Chưa có" : winner);
            setText(lblSelectedAuctionEndTime, DateTimes.format(endTime));
        } else {
            setText(lblSelectedAuctionStatus,  "📦 Chưa đăng");
            setText(lblSelectedAuctionPrice,   MoneyFormatter.formatViaPattern(item.getStartingPrice()));
            setText(lblSelectedAuctionWinner,  "Chưa có");
            setText(lblSelectedAuctionEndTime, "--");
        }
        loadSellerProductImage(item.getImagePath());
    }

    private void loadSellerProductImage(String path) {
        try {
            if (path != null && !path.isBlank() && sellerProductImage != null) {
                Image img = new Image(
                        path.startsWith("file:") || path.startsWith("http")
                                ? path : new File(path).toURI().toString(), true);
                sellerProductImage.setImage(img);
                if (!img.isError()) {
                    if (sellerProductImagePlaceholder != null) {
                        sellerProductImagePlaceholder.setVisible(false);
                        sellerProductImagePlaceholder.setManaged(false);
                    }
                    return;
                }
            }
        } catch (Exception ignored) { }
        showSellerImagePlaceholder();
    }

    private void showSellerImagePlaceholder() {
        if (sellerProductImage != null) sellerProductImage.setImage(null);
        if (sellerProductImagePlaceholder != null) {
            sellerProductImagePlaceholder.setVisible(true);
            sellerProductImagePlaceholder.setManaged(true);
        }
    }

    @FXML
    private void handleOpenAuctionRoom() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Dialogs.warn("Nhắc nhở", "Vui lòng chọn sản phẩm có phiên đấu giá!");
            return;
        }
        Map<String, Object> auction = auctionMap.get(selectedItem.getItemId());
        if (auction == null || auction.get("auctionId") == null) {
            Dialogs.warn("Không có phiên", "Sản phẩm này chưa có phòng đấu giá để xem.");
            return;
        }
        try {
            int auctionId = MapAccessor.getInt(auction, "auctionId");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/auction-detail.fxml"));
            Parent root = loader.load();
            AuctionDetailController controller = loader.getController();
            controller.setRoomId(String.valueOf(auctionId));
            Scene currentScene = tableItems.getScene();
            currentScene.setRoot(root);
            Stage stage = (Stage) currentScene.getWindow();
            stage.setTitle("Phòng đấu giá #" + auctionId + " - AuctionVN");
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error("Lỗi hệ thống", "Không thể mở phòng đấu giá.");
        }
    }

    @FXML
    private void handleShowSelectedProductDetail() {
        Item selectedItem = tableItems == null ? null : tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Dialogs.warn("Chưa chọn sản phẩm", "Hãy chọn một sản phẩm trong bảng để xem thông tin đầy đủ.");
            return;
        }

        Map<String, Object> auction = auctionMap.get(selectedItem.getItemId());
        VBox content = new VBox(16);
        content.setPadding(new Insets(4));

        Label title = new Label(SafeParser.safe(selectedItem.getName()).isBlank()
                ? "Sản phẩm" : selectedItem.getName());
        title.getStyleClass().add("popup-main-title");
        Label subtitle = new Label("Thông tin chi tiết dành cho Seller — có thể cuộn để đọc mô tả dài.");
        subtitle.getStyleClass().add("popup-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(10);
        StyledComponents.addInfoRow(grid, 0, "Mã sản phẩm",  SafeParser.safe(selectedItem.getItemId()));
        StyledComponents.addInfoRow(grid, 1, "Danh mục",      StatusMapper.normalizeCategory(selectedItem.getCategory()));
        StyledComponents.addInfoRow(grid, 2, "Giá khởi điểm", MoneyFormatter.formatViaPattern(selectedItem.getStartingPrice()));
        StyledComponents.addInfoRow(grid, 3, "Bước giá",
                selectedItem.getBidIncrement() > 0 ? MoneyFormatter.formatViaPattern(selectedItem.getBidIncrement()) : "--");
        StyledComponents.addInfoRow(grid, 4, "Trạng thái phiên",
                auction == null ? "📦 Chưa đăng"
                        : StatusMapper.toSellerText(MapAccessor.getString(auction, "status", "NONE")));
        StyledComponents.addInfoRow(grid, 5, "Giá hiện tại",
                auction == null
                        ? MoneyFormatter.formatViaPattern(selectedItem.getStartingPrice())
                        : MoneyFormatter.formatViaPattern(MapAccessor.getDouble(auction, "currentPrice", selectedItem.getStartingPrice())));
        StyledComponents.addInfoRow(grid, 6, "Người dẫn đầu",
                auction == null ? "Chưa có"
                        : MapAccessor.getString(auction, "currentWinner", "Chưa có"));
        StyledComponents.addInfoRow(grid, 7, "Thời gian kết thúc",
                auction == null ? "--" : DateTimes.format(MapAccessor.getString(auction, "endTime")));
        StyledComponents.addInfoRow(grid, 8, "Mã phiên",
                auction == null ? "--" : MapAccessor.getString(auction, "auctionId", "--"));

        Label descTitle = new Label("Mô tả sản phẩm");
        descTitle.getStyleClass().add("section-title");
        TextArea descArea = StyledComponents.readonlyArea(
                SafeParser.safe(selectedItem.getDescription()).isBlank()
                        ? "Chưa có mô tả." : selectedItem.getDescription(), 220);

        Label hint = new Label("Mẹo thao tác: double-click sản phẩm để sửa, Delete để xóa, F5 để làm mới.");
        hint.setWrapText(true);
        hint.getStyleClass().add("popup-hint");

        content.getChildren().addAll(title, subtitle, grid, descTitle, descArea, hint);
        StyledComponents.showScrollable("Chi tiết sản phẩm", content, 760, 650);
    }

    @FXML
    private void handleShowSelectedProductImage() {
        Item selectedItem = tableItems == null ? null : tableItems.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Dialogs.warn("Chưa chọn sản phẩm", "Hãy chọn một sản phẩm để xem ảnh lớn.");
            return;
        }

        Image image = sellerProductImage == null ? null : sellerProductImage.getImage();
        if (image == null && selectedItem.getImagePath() != null && !selectedItem.getImagePath().isBlank()) {
            try {
                image = new Image(
                        selectedItem.getImagePath().startsWith("file:") || selectedItem.getImagePath().startsWith("http")
                                ? selectedItem.getImagePath()
                                : new File(selectedItem.getImagePath()).toURI().toString(), true);
            } catch (Exception ignored) { }
        }

        if (image == null) {
            Dialogs.warn("Ảnh sản phẩm", "Sản phẩm này chưa có ảnh hoặc đường dẫn ảnh không đọc được.");
            return;
        }

        VBox content = new VBox(14);
        content.setAlignment(Pos.CENTER);
        Label title = new Label(SafeParser.safe(selectedItem.getName()).isBlank()
                ? "Ảnh sản phẩm" : selectedItem.getName());
        title.getStyleClass().add("popup-main-title");
        ImageView preview = new ImageView(image);
        preview.setFitWidth(760); preview.setFitHeight(520);
        preview.setPreserveRatio(true); preview.setSmooth(true);
        preview.getStyleClass().add("popup-image-preview");
        Label path = new Label(SafeParser.safe(selectedItem.getImagePath()).isBlank()
                ? "Không có đường dẫn ảnh." : selectedItem.getImagePath());
        path.setWrapText(true);
        path.getStyleClass().add("popup-subtitle");
        content.getChildren().addAll(title, preview, path);
        StyledComponents.showScrollable("Xem ảnh sản phẩm", content, 860, 720);
    }

    // ─── Stats ──────────────────────────────────────────────────────

    private void updateMiniStats() {
        int totalItems = itemList == null ? 0 : itemList.size();
        int running = 0;
        int finished = 0;
        double totalRevenue = 0;

        if (itemList != null) {
            for (Item item : itemList) {
                Map<String, Object> auction = auctionMap.get(item.getItemId());
                if (auction == null) continue;
                String status = MapAccessor.getString(auction, "status", "NONE");
                double price = MapAccessor.getDouble(auction, "currentPrice", item.getStartingPrice());
                if ("RUNNING".equals(status)) running++;
                if ("FINISHED".equals(status) || "PAID".equals(status)) {
                    finished++; totalRevenue += price;
                }
            }
        }

        setText(lblSellerItemsCount,  String.valueOf(totalItems));
        setText(lblSellerRunningCount, String.valueOf(running));
        setText(lblSellerRevenueMini, MoneyFormatter.formatViaPattern(totalRevenue));
        setText(lblTotalItems,        String.valueOf(totalItems));
        setText(lblRunningAuctions,   String.valueOf(running));
        setText(lblFinishedAuctions,  String.valueOf(finished));
        setText(lblTotalRevenue,      MoneyFormatter.formatViaPattern(totalRevenue));
    }

    private void setSellerNavActive(Button active) {
        for (Button button : List.of(btnInventory, btnReport)) {
            if (button == null) continue;
            button.getStyleClass().remove("nav-button-active");
            if (!button.getStyleClass().contains("nav-button")) button.getStyleClass().add("nav-button");
        }
        if (active != null) {
            active.getStyleClass().remove("nav-button");
            if (!active.getStyleClass().contains("nav-button-active"))
                active.getStyleClass().add("nav-button-active");
        }
    }

    private void updateReport() {
        int totalItems = itemList == null ? 0 : itemList.size();
        int running = 0, finished = 0;
        double totalRevenue = 0;

        revenueBarChart.getData().clear();
        statusPieChart.getData().clear();

        XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
        if (itemList != null) {
            for (Item item : itemList) {
                Map<String, Object> auction = auctionMap.get(item.getItemId());
                double price = item.getStartingPrice();
                String status = "NONE";
                if (auction != null) {
                    price  = MapAccessor.getDouble(auction, "currentPrice", price);
                    status = MapAccessor.getString(auction, "status", "NONE");
                }
                if ("RUNNING".equals(status)) running++;
                if ("FINISHED".equals(status) || "PAID".equals(status)) {
                    finished++; totalRevenue += price;
                }
                revenueSeries.getData().add(new XYChart.Data<>(item.getName(), price));
            }
        }

        setText(lblTotalItems,       String.valueOf(totalItems));
        setText(lblRunningAuctions,  String.valueOf(running));
        setText(lblFinishedAuctions, String.valueOf(finished));
        setText(lblTotalRevenue,     MoneyFormatter.formatViaPattern(totalRevenue));

        revenueBarChart.getData().add(revenueSeries);

        int notStarted = Math.max(totalItems - running - finished, 0);
        statusPieChart.getData().add(new PieChart.Data("Đang đấu giá", running));
        statusPieChart.getData().add(new PieChart.Data("Đã kết thúc", finished));
        statusPieChart.getData().add(new PieChart.Data("Chưa đăng", notStarted));
    }

    private void setText(Label label, String text) {
        if (label != null) label.setText(text);
    }
}
