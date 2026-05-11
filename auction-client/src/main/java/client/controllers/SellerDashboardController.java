package client.controllers;

import client.models.item.Art;
import client.models.item.Item;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SellerDashboardController {

    private String formatVND(double amount) {
        return String.format("%,.0f đ", amount).replace(",", ".");
    }

    @FXML private TableView<Item> tableItems;
    @FXML private TableColumn<Item, String> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, String> colWinner;
    @FXML private TableColumn<Item, String> colStatus;

    @FXML private VBox inventoryView;
    @FXML private VBox reportView;
    @FXML private Button btnInventory;
    @FXML private Button btnReport;

    @FXML private Label lblTotalItems;
    @FXML private Label lblRunningAuctions;
    @FXML private Label lblFinishedAuctions;
    @FXML private Label lblTotalRevenue;

    @FXML private BarChart<String, Number> revenueBarChart;
    @FXML private PieChart statusPieChart;

    private ObservableList<Item> itemList;
    private final Gson gson = new Gson();
    private final Map<String, Map<String, Object>> auctionMap = new HashMap<>();

    // Hàng đợi roomId các phiên đấu giá vừa kết thúc — chờ MY_AUCTIONS reload xong
    // để biết ai thắng và bao nhiêu rồi mới hiện thông báo cho Seller.
    private final java.util.Queue<Long> pendingFinishedNotifications =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    @FXML
    public void initialize() {
        itemList = FXCollections.observableArrayList();
        tableItems.setItems(itemList);

        Label emptyLabel = new Label("Kho hàng đang trống. Hãy thêm sản phẩm mới!");
        emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-style: italic;");
        tableItems.setPlaceholder(emptyLabel);

        colId.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        colPrice.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            Map<String, Object> auction = auctionMap.get(item.getItemId());

            double price = auction != null && auction.get("currentPrice") != null
                    ? Double.parseDouble(auction.get("currentPrice").toString())
                    : item.getStartingPrice();

            return new javafx.beans.property.SimpleObjectProperty<>(price);
        });

        colPrice.setCellFactory(column -> new TableCell<Item, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : formatVND(price));
            }
        });

        colWinner.setText("Thông tin chi tiết");
        colWinner.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDetails()));

        colWinner.setCellFactory(column -> new TableCell<Item, String>() {
            private final Label label = new Label();

            {
                label.setWrapText(true);
                label.setStyle("-fx-text-fill: #334155; -fx-line-spacing: 3px;");
            }

            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);

                if (empty || text == null || text.isBlank()) {
                    setGraphic(null);
                } else {
                    label.setText(text);
                    label.setMaxWidth(colWinner.getWidth() - 20);
                    setGraphic(label);
                }
            }
        });

        colStatus.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            Map<String, Object> auction = auctionMap.get(item.getItemId());

            String status = auction != null && auction.get("status") != null
                    ? auction.get("status").toString()
                    : "NONE";

            return new SimpleStringProperty(statusToText(status));
        });

        colStatus.setCellFactory(column -> new TableCell<Item, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }

                Label badge = new Label(status);
                badge.setStyle(stylForStatus(status));
                setGraphic(badge);
            }
        });

        ClientMain.registerListener("AUCTION_STARTED", payload ->
                Platform.runLater(this::loadMyAuctionsFromServer));

        // Khi phòng đấu giá của Seller kết thúc — broadcast payload chỉ là roomId.
        // Reload danh sách rồi tìm phòng đó để show thông báo chi tiết (ai thắng, giá cuối).
        ClientMain.registerListener("AUCTION_FINISHED", payload -> {
            try {
                long finishedRoomId = Long.parseLong(payload.trim());
                pendingFinishedNotifications.add(finishedRoomId);
            } catch (Exception ignored) {}
            Platform.runLater(this::loadMyAuctionsFromServer);
        });

        // Khi Admin hủy phòng của Seller — cũng cần reload + thông báo
        ClientMain.registerListener("AUCTION_CANCELED", payload -> {
            try {
                long canceledRoomId = Long.parseLong(payload.trim());
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.WARNING);
                    a.setTitle("Phiên đấu giá bị hủy");
                    a.setHeaderText(null);
                    a.setContentText("⚠️ Phiên đấu giá #" + canceledRoomId + " đã bị quản trị viên hủy.");
                    a.showAndWait();
                });
            } catch (Exception ignored) {}
            Platform.runLater(this::loadMyAuctionsFromServer);
        });

        // Bắt mọi lỗi chung từ server
        ClientMain.registerListener("ERROR", payload ->
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setTitle("Lỗi từ máy chủ");
                    a.setHeaderText(null);
                    a.setContentText(payload);
                    a.showAndWait();
                })
        );

        loadMyItemsFromServer();
    }

    private String statusToText(String status) {
        return switch (status) {
            case "OPEN" -> "⏳ Sắp bắt đầu";
            case "RUNNING" -> "🔴 Đang đấu giá";
            case "FINISHED" -> "✅ Kết thúc";
            case "PAID" -> "💰 Đã thanh toán";
            case "CANCELED" -> "❌ Đã hủy";
            default -> "📦 Chưa đăng";
        };
    }

    private String stylForStatus(String status) {
        String base = "-fx-padding: 3 8; -fx-background-radius: 5; -fx-font-weight: bold; -fx-font-size: 11px;";

        return switch (status) {
            case "🔴 Đang đấu giá" -> base + "-fx-background-color: #dcfce7; -fx-text-fill: #166534;";
            case "⏳ Sắp bắt đầu" -> base + "-fx-background-color: #fef9c3; -fx-text-fill: #854d0e;";
            case "✅ Kết thúc" -> base + "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";
            case "💰 Đã thanh toán" -> base + "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af;";
            case "❌ Đã hủy" -> base + "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
            default -> base + "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";
        };
    }

    private void loadMyItemsFromServer() {
        ClientMain.registerListener("MY_ITEMS", payload -> {
            ClientMain.unregisterListener("MY_ITEMS");

            try {
                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> list = gson.fromJson(payload, listType);

                Platform.runLater(() -> {
                    itemList.clear();

                    if (list != null) {
                        for (Map<String, Object> m : list) {
                            String itemId = m.get("itemId") != null ? m.get("itemId").toString() : "";
                            String name = m.get("name") != null ? m.get("name").toString() : "";
                            String description = m.get("description") != null ? m.get("description").toString() : "";

                            String categoryInfo = "";

                            if (m.get("categoryInfo") != null) {
                                categoryInfo = m.get("categoryInfo").toString();
                            } else if (m.get("CategoryInfo") != null) {
                                categoryInfo = m.get("CategoryInfo").toString();
                            }

                            double price = m.get("startingPrice") != null
                                    ? Double.parseDouble(m.get("startingPrice").toString())
                                    : 0;

                            Item item = new Art(itemId, name, price, categoryInfo);
                            item.setDescription(description);

                            if (m.get("bidIncrement") != null) {
                                item.setBidIncrement(Double.parseDouble(m.get("bidIncrement").toString()));
                            }

                            if (m.get("imagePath") != null) {
                                item.setImagePath(m.get("imagePath").toString());
                            }

                            itemList.add(item);
                        }
                    }

                    loadMyAuctionsFromServer();

                    if (reportView != null && reportView.isVisible()) {
                        updateReport();
                    }
                });
            } catch (Exception e) {
                System.err.println("Lỗi parse MY_ITEMS: " + e.getMessage());
            }
        });

        new Thread(() ->
                ClientMain.send(gson.toJson(new MessageDTO("GET_MY_ITEMS", "")))
        ).start();
    }

    private void loadMyAuctionsFromServer() {
        ClientMain.registerListener("MY_AUCTIONS", payload -> {
            ClientMain.unregisterListener("MY_AUCTIONS");

            try {
                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> list = gson.fromJson(payload, listType);

                Platform.runLater(() -> {
                    auctionMap.clear();

                    // Index theo cả itemId (cho table) lẫn auctionId (để tra notify)
                    Map<Long, Map<String, Object>> byAuctionId = new HashMap<>();
                    if (list != null) {
                        for (Map<String, Object> a : list) {
                            String itemId = a.get("itemId") != null ? a.get("itemId").toString() : "";
                            auctionMap.put(itemId, a);
                            if (a.get("auctionId") != null) {
                                try {
                                    long aid = ((Number) a.get("auctionId")).longValue();
                                    byAuctionId.put(aid, a);
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    tableItems.refresh();

                    if (reportView != null && reportView.isVisible()) {
                        updateReport();
                    }

                    // Xử lý notification cho các phiên vừa kết thúc
                    Long finishedId;
                    while ((finishedId = pendingFinishedNotifications.poll()) != null) {
                        Map<String, Object> room = byAuctionId.get(finishedId);
                        if (room != null) {
                            showAuctionFinishedNotification(finishedId, room);
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("Lỗi parse MY_AUCTIONS: " + e.getMessage());
            }
        });

        new Thread(() ->
                ClientMain.send(gson.toJson(new MessageDTO("GET_MY_AUCTIONS", "")))
        ).start();
    }

    /**
     * Hiển thị thông báo cho Seller khi phiên đấu giá của họ kết thúc.
     * Phân biệt 2 trường hợp: bán được (có winner) và không có ai bid (CANCELED).
     */
    private void showAuctionFinishedNotification(long roomId, Map<String, Object> room) {
        String itemName = room.get("itemName") != null ? room.get("itemName").toString() : "?";
        String winner   = room.get("currentWinner") != null ? room.get("currentWinner").toString() : "";
        String status   = room.get("status") != null ? room.get("status").toString() : "";
        double price    = room.get("currentPrice") != null
                ? ((Number) room.get("currentPrice")).doubleValue() : 0;

        Alert a;
        if ("CANCELED".equalsIgnoreCase(status) || winner.isEmpty()) {
            a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Phiên đấu giá kết thúc");
            a.setContentText(
                    "😔 Phiên #" + roomId + " (" + itemName + ") đã kết thúc nhưng không có người mua.\n\n" +
                            "Bạn có thể tạo lại phiên đấu giá mới."
            );
        } else {
            a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Bán hàng thành công!");
            a.setContentText(
                    "🎉 Phiên #" + roomId + " (" + itemName + ") đã kết thúc!\n\n" +
                            "Người thắng: " + winner + "\n" +
                            "Giá cuối:    " + formatVND(price)
            );
        }
        a.setHeaderText(null);
        a.show();
    }

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

            if (newItem != null) {
                ClientMain.registerListener("ADD_ITEM_SUCCESS", payload -> {
                    ClientMain.unregisterListener("ADD_ITEM_SUCCESS");
                    ClientMain.unregisterListener("ADD_ITEM_FAILED");

                    Platform.runLater(() -> {
                        showAlert("Thành công", "Đã thêm sản phẩm!");
                        loadMyItemsFromServer();
                    });
                });

                ClientMain.registerListener("ADD_ITEM_FAILED", payload -> {
                    ClientMain.unregisterListener("ADD_ITEM_SUCCESS");
                    ClientMain.unregisterListener("ADD_ITEM_FAILED");
                    Platform.runLater(() -> showAlert("Lỗi", "Thêm thất bại: " + payload));
                });

                ClientMain.send(gson.toJson(new MessageDTO("ADD_ITEM", gson.toJson(newItem))));
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể mở form thêm sản phẩm.");
        }
    }

    @FXML
    private void handleStartAuction() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn sản phẩm để tạo phiên đấu giá!");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Tạo phiên đấu giá");
        dialog.setHeaderText(null);

        ButtonType btnOk = new ButtonType("🚀 Bắt đầu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnOk, ButtonType.CANCEL);

        VBox root = new VBox(18);
        root.setPadding(new javafx.geometry.Insets(25));
        root.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 18;
                """);

        VBox header = new VBox(6);
        Label title = new Label("Tạo phiên đấu giá");
        title.setStyle("""
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: #0f172a;
                """);

        Label subTitle = new Label("Thiết lập thời gian bắt đầu và thời lượng phiên đấu giá");
        subTitle.setStyle("""
                -fx-font-size: 13px;
                -fx-text-fill: #64748b;
                """);

        Label productName = new Label("📦 Sản phẩm: " + selectedItem.getName());
        productName.setStyle("""
                -fx-background-color: #eff6ff;
                -fx-text-fill: #1d4ed8;
                -fx-font-weight: bold;
                -fx-padding: 10 14;
                -fx-background-radius: 10;
                """);

        header.getChildren().addAll(title, subTitle, productName);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);

        DatePicker datePicker = new DatePicker(java.time.LocalDate.now());

        TextField txtTime = new TextField(
                java.time.LocalTime.now()
                        .plusMinutes(5)
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        );

        TextField txtDuration = new TextField("30");

        datePicker.setMaxWidth(Double.MAX_VALUE);
        txtTime.setMaxWidth(Double.MAX_VALUE);
        txtDuration.setMaxWidth(Double.MAX_VALUE);

        String inputStyle = """
                -fx-background-color: #f8fafc;
                -fx-border-color: #cbd5e1;
                -fx-border-radius: 10;
                -fx-background-radius: 10;
                -fx-padding: 10;
                -fx-font-size: 14px;
                """;

        datePicker.setStyle(inputStyle);
        txtTime.setStyle(inputStyle);
        txtDuration.setStyle(inputStyle);

        Label lblDate = createAuctionLabel("Ngày bắt đầu");
        Label lblTime = createAuctionLabel("Giờ bắt đầu");
        Label lblDuration = createAuctionLabel("Thời gian đấu giá");

        grid.add(lblDate, 0, 0);
        grid.add(datePicker, 1, 0);

        grid.add(lblTime, 0, 1);
        grid.add(txtTime, 1, 1);

        grid.add(lblDuration, 0, 2);
        grid.add(txtDuration, 1, 2);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPrefWidth(150);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPrefWidth(260);
        c2.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(c1, c2);

        Label note = new Label("Gợi ý: thời gian nên đặt sau hiện tại vài phút để bidder kịp tham gia.");
        note.setWrapText(true);
        note.setStyle("""
                -fx-background-color: #fefce8;
                -fx-text-fill: #854d0e;
                -fx-padding: 10 14;
                -fx-background-radius: 10;
                -fx-font-size: 12px;
                """);

        root.getChildren().addAll(header, grid, note);

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 18;
                """);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(btnOk);
        okButton.setStyle("""
                -fx-background-color: #10b981;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-background-radius: 10;
                -fx-padding: 10 22;
                -fx-cursor: hand;
                """);

        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setText("Hủy bỏ");
        cancelButton.setStyle("""
                -fx-background-color: #f1f5f9;
                -fx-text-fill: #475569;
                -fx-font-weight: bold;
                -fx-background-radius: 10;
                -fx-padding: 10 22;
                -fx-cursor: hand;
                """);

        dialog.setResultConverter(btn -> {
            if (btn == btnOk) {
                return datePicker.getValue() + "T" + txtTime.getText() + ":" + txtDuration.getText();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(data -> {
            try {
                String[] parts = data.split(":");
                String startTime = parts[0] + ":" + parts[1];
                String duration = parts[2];
                String payload = selectedItem.getItemId() + ":" + startTime + ":" + duration;

                ClientMain.registerListener("CREATE_AUCTION_SUCCESS", p -> {
                    ClientMain.unregisterListener("CREATE_AUCTION_SUCCESS");
                    ClientMain.unregisterListener("CREATE_AUCTION_FAILED");

                    Platform.runLater(() -> {
                        showAlert("Thành công", "Phiên đấu giá sẽ bắt đầu lúc " + startTime.replace("T", " "));
                        loadMyAuctionsFromServer();
                    });
                });

                ClientMain.registerListener("CREATE_AUCTION_FAILED", p -> {
                    ClientMain.unregisterListener("CREATE_AUCTION_SUCCESS");
                    ClientMain.unregisterListener("CREATE_AUCTION_FAILED");
                    Platform.runLater(() -> showAlert("Lỗi", "Tạo phiên thất bại: " + p));
                });

                new Thread(() ->
                        ClientMain.send(gson.toJson(new MessageDTO("CREATE_AUCTION", payload)))
                ).start();

            } catch (Exception e) {
                showAlert("Lỗi nhập liệu", "Vui lòng nhập đúng định dạng giờ HH:mm và thời gian là số phút.");
            }
        });
    }

    private Label createAuctionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("""
                -fx-font-weight: bold;
                -fx-text-fill: #334155;
                -fx-font-size: 13px;
                """);
        return label;
    }

    @FXML
    private void handleEditProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm để chỉnh sửa!");
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

            if (updatedItem != null) {
                final Item finalUpdated = updatedItem;
                final int  finalIndex   = itemList.indexOf(selectedItem);

                ClientMain.registerListener("UPDATE_ITEM_SUCCESS", payload -> {
                    ClientMain.unregisterListener("UPDATE_ITEM_SUCCESS");
                    ClientMain.unregisterListener("UPDATE_ITEM_FAILED");
                    Platform.runLater(() -> {
                        itemList.set(finalIndex, finalUpdated);
                        tableItems.refresh();
                        showSuccess("Cập nhật sản phẩm thành công!");
                    });
                });

                ClientMain.registerListener("UPDATE_ITEM_FAILED", payload -> {
                    ClientMain.unregisterListener("UPDATE_ITEM_SUCCESS");
                    ClientMain.unregisterListener("UPDATE_ITEM_FAILED");
                    Platform.runLater(() ->
                            showAlert("Cập nhật thất bại", "Server báo lỗi: " + payload)
                    );
                });

                ClientMain.send(gson.toJson(new MessageDTO(
                        "UPDATE_ITEM",
                        gson.toJson(updatedItem)
                )));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể mở form sửa sản phẩm.");
        }
    }

    @FXML
    private void handleDeleteProduct() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn một sản phẩm để xóa!");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Xác nhận xóa");
        confirmDialog.setHeaderText("Xóa sản phẩm: " + selectedItem.getName());
        confirmDialog.setContentText("Bạn có chắc chắn muốn xóa?");

        Optional<ButtonType> result = confirmDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            final Item toDelete = selectedItem;

            ClientMain.registerListener("DELETE_ITEM_SUCCESS", payload -> {
                ClientMain.unregisterListener("DELETE_ITEM_SUCCESS");
                ClientMain.unregisterListener("DELETE_ITEM_FAILED");
                Platform.runLater(() -> {
                    itemList.remove(toDelete);
                    tableItems.refresh();
                    if (reportView != null && reportView.isVisible()) updateReport();
                    showSuccess("Đã xóa sản phẩm \"" + toDelete.getName() + "\".");
                });
            });

            ClientMain.registerListener("DELETE_ITEM_FAILED", payload -> {
                ClientMain.unregisterListener("DELETE_ITEM_SUCCESS");
                ClientMain.unregisterListener("DELETE_ITEM_FAILED");
                Platform.runLater(() ->
                        showAlert("Xóa thất bại", "Server báo lỗi: " + payload)
                );
            });

            ClientMain.send(gson.toJson(new MessageDTO("DELETE_ITEM", selectedItem.getItemId())));
        }
    }

    @FXML
    private void handleCancelAuction() {
        Item selectedItem = tableItems.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Nhắc nhở", "Vui lòng chọn sản phẩm có phiên đấu giá để hủy!");
            return;
        }

        Map<String, Object> auction = auctionMap.get(selectedItem.getItemId());
        if (auction == null || auction.get("auctionId") == null) {
            showAlert("Không có phiên", "Sản phẩm này chưa có phiên đấu giá nào!");
            return;
        }

        String status = auction.get("status") != null ? auction.get("status").toString() : "";
        if (status.equals("PAID") || status.equals("FINISHED") || status.equals("CANCELED")) {
            showAlert("Không thể hủy",
                    "Phiên đấu giá đã ở trạng thái " + statusToText(status) + " — không thể hủy.");
            return;
        }

        // Lấy auctionId an toàn (server trả về kiểu Number)
        int auctionId;
        try {
            auctionId = ((Number) auction.get("auctionId")).intValue();
        } catch (Exception e) {
            showAlert("Lỗi dữ liệu", "Không đọc được ID phiên đấu giá.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy phiên");
        confirm.setHeaderText("Hủy phiên đấu giá: " + selectedItem.getName());
        confirm.setContentText(
                "Phiên #" + auctionId + " sẽ bị hủy.\n"
                        + "Lưu ý: nếu phiên đang chạy đã có người đặt giá, server sẽ từ chối.\n\n"
                        + "Bạn chắc chắn chứ?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        ClientMain.registerListener("DELETE_AUCTION_SUCCESS", payload -> {
            ClientMain.unregisterListener("DELETE_AUCTION_SUCCESS");
            ClientMain.unregisterListener("DELETE_AUCTION_FAILED");
            Platform.runLater(() -> {
                showSuccess(payload);
                loadMyAuctionsFromServer();
            });
        });

        ClientMain.registerListener("DELETE_AUCTION_FAILED", payload -> {
            ClientMain.unregisterListener("DELETE_AUCTION_SUCCESS");
            ClientMain.unregisterListener("DELETE_AUCTION_FAILED");
            Platform.runLater(() ->
                    showAlert("Hủy phiên thất bại", "Server báo: " + payload)
            );
        });

        ClientMain.send(gson.toJson(new MessageDTO("DELETE_AUCTION", String.valueOf(auctionId))));
    }

    @FXML
    private void handleLogout() {
        ClientMain.unregisterListener("AUCTION_STARTED");
        ClientMain.unregisterListener("AUCTION_FINISHED");
        ClientMain.unregisterListener("AUCTION_CANCELED");
        ClientMain.unregisterListener("ERROR");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/login.fxml"));
            Parent root = loader.load();

            Scene currentScene = tableItems.getScene();
            currentScene.setRoot(root);

            Stage stage = (Stage) currentScene.getWindow();
            stage.setTitle("Đăng nhập - AuctionVN");
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể tải màn hình đăng nhập!");
        }
    }

    @FXML
    void handleEditProduct(ActionEvent event) {
        handleEditProduct();
    }

    @FXML
    private void handleShowInventory() {
        inventoryView.setVisible(true);
        inventoryView.setManaged(true);

        reportView.setVisible(false);
        reportView.setManaged(false);

        btnInventory.getStyleClass().add("sidebar-btn-active");
        btnReport.getStyleClass().remove("sidebar-btn-active");
    }

    @FXML
    private void handleShowReport() {
        inventoryView.setVisible(false);
        inventoryView.setManaged(false);

        reportView.setVisible(true);
        reportView.setManaged(true);

        btnReport.getStyleClass().add("sidebar-btn-active");
        btnInventory.getStyleClass().remove("sidebar-btn-active");

        updateReport();
    }

    @FXML
    private void handleRefreshReport() {
        loadMyItemsFromServer();
        loadMyAuctionsFromServer();
        updateReport();
    }

    private void updateReport() {
        int totalItems = itemList == null ? 0 : itemList.size();
        int running = 0;
        int finished = 0;
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
                    if (auction.get("currentPrice") != null) {
                        price = Double.parseDouble(auction.get("currentPrice").toString());
                    }

                    if (auction.get("status") != null) {
                        status = auction.get("status").toString();
                    }
                }

                if ("RUNNING".equals(status)) {
                    running++;
                }

                if ("FINISHED".equals(status) || "PAID".equals(status)) {
                    finished++;
                    totalRevenue += price;
                }

                revenueSeries.getData().add(new XYChart.Data<>(item.getName(), price));
            }
        }

        lblTotalItems.setText(String.valueOf(totalItems));
        lblRunningAuctions.setText(String.valueOf(running));
        lblFinishedAuctions.setText(String.valueOf(finished));
        lblTotalRevenue.setText(formatVND(totalRevenue));

        revenueBarChart.getData().add(revenueSeries);

        int notStarted = Math.max(totalItems - running - finished, 0);

        statusPieChart.getData().add(new PieChart.Data("Đang đấu giá", running));
        statusPieChart.getData().add(new PieChart.Data("Đã kết thúc", finished));
        statusPieChart.getData().add(new PieChart.Data("Chưa đăng", notStarted));
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showSuccess(String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thành công");
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}