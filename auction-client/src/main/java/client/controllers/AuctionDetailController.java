package client.controllers;

import client.models.user.UserSession;
import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class AuctionDetailController implements Initializable {

    @FXML private Label lblCurrentPrice, lblWinner, lblTimer;
    @FXML private Label lblRoomTitle, lblRoomId, lblStatusBadge;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid, btnOpenAutoBid;
    @FXML private HBox paneAutoBidActive;
    @FXML private Label lblAutoBidInfo;
    @FXML private ListView<String> historyList;
    @FXML private ListView<String> myBidHistoryList;
    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private Label lblAvatar;
    @FXML private Label lblProfileName, lblProfileRole;
    @FXML private Label lblProfileBalance, lblProfileBidCount;
    @FXML private Label lblProfileWins, lblProfileWinRate;
    @FXML private Label lblRankIcon, lblRankTitle, lblRankSub;
    @FXML private Label lblMyBestBid;
    @FXML private javafx.scene.layout.VBox overlayFinished;
    @FXML private Label lblFinishIcon, lblFinishTitle, lblFinishMessage;

    private String currentRoomId;
    private String myUsername;
    private volatile int remainingSeconds = 0;
    private Timer timer;
    private final Gson gson = new Gson();
    private XYChart.Series<String, Number> priceSeries;
    private int    bidCount         = 0;
    private double currentPriceVal  = 0;
    private String lastWinner       = "";
    private int    myBidCountInRoom = 0;
    private double myBestBid        = 0;
    private boolean isAutoBidActive = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        myUsername = UserSession.getInstance().getUsername();
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Diễn biến giá");
        if (bidHistoryChart != null) bidHistoryChart.getData().add(priceSeries);
        setupUserProfile();
        registerServerListeners();
    }

    private void setupUserProfile() {
        String name = myUsername != null ? myUsername : "--";
        lblProfileName.setText(name);
        lblProfileRole.setText(mapRoleLabel(UserSession.getInstance().getRole()));
        String initials = name.length() >= 2
                ? (name.substring(0,1) + name.substring(1,2)).toUpperCase()
                : name.toUpperCase();
        lblAvatar.setText(initials);
        lblProfileBalance.setText(formatVND(UserSession.getInstance().getBalance()));
        lblProfileBidCount.setText("0");
        lblProfileWins.setText("--");
        lblProfileWinRate.setText("--");
        lblRankTitle.setText("Đang tải...");
        lblRankSub.setText("");
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

    private void updateRankBadge(int wins) {
        String icon, title, sub, bg, border, tc;
        if (wins >= 50) {
            icon="💎"; title="Hạng Kim Cương"; sub="Top 1% người dùng";
            bg="#EDE9FE"; border="#C4B5FD"; tc="#4C1D95";
        } else if (wins >= 20) {
            icon="🥇"; title="Hạng Vàng"; sub="Top 10% người dùng";
            bg="#FFFBEB"; border="#FDE68A"; tc="#92400E";
        } else if (wins >= 5) {
            icon="🥈"; title="Hạng Bạc"; sub="Top 30% người dùng";
            bg="#F8FAFC"; border="#CBD5E1"; tc="#475569";
        } else {
            icon="🥉"; title="Hạng Đồng"; sub="Người mới tham gia";
            bg="#FFF7ED"; border="#FED7AA"; tc="#C2410C";
        }
        final String fi=icon, ft=title, fs=sub, fbg=bg, fbd=border, ftc=tc;
        Platform.runLater(() -> {
            lblRankIcon.setText(fi);
            lblRankTitle.setText(ft);
            lblRankTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: "+ftc+";");
            lblRankSub.setText(fs);
            lblRankSub.setStyle("-fx-font-size: 11px; -fx-text-fill: "+ftc+";");
            if (lblRankIcon.getParent() != null && lblRankIcon.getParent().getParent() != null) {
                lblRankIcon.getParent().getParent().setStyle(
                        "-fx-background-color: "+fbg+"; -fx-background-radius: 8; -fx-padding: 10 14;"
                                +"-fx-border-color: "+fbd+"; -fx-border-radius: 8;");
            }
        });
    }

    private void registerServerListeners() {

        ClientMain.registerListener("AUCTION_DETAIL_DATA", payload -> {
            String[] data = payload.split(":");
            if (data.length < 3) return;
            Platform.runLater(() -> {
                currentPriceVal = Double.parseDouble(data[0]);
                lblCurrentPrice.setText(formatVND(currentPriceVal));
                remainingSeconds = Integer.parseInt(data[1]);
                String status = data[2];
                boolean canBid = "RUNNING".equalsIgnoreCase(status);
                btnPlaceBid.setDisable(!canBid);
                btnOpenAutoBid.setDisable(!canBid);
                updateStatusBadge(status);
                priceSeries.getData().clear();
                bidCount = 0;
                priceSeries.getData().add(new XYChart.Data<>("Bắt đầu", currentPriceVal));
                startTimer();
            });
        });

        ClientMain.registerListener("BID_HISTORY", payload -> {
            try {
                java.lang.reflect.Type lt =
                        new com.google.gson.reflect.TypeToken<List<Map<String,Object>>>(){}.getType();
                List<Map<String,Object>> bids = gson.fromJson(payload, lt);
                Platform.runLater(() -> {
                    historyList.getItems().clear();
                    myBidHistoryList.getItems().clear();
                    priceSeries.getData().clear();
                    bidCount = 0; myBidCountInRoom = 0; myBestBid = 0;
                    if (bids == null || bids.isEmpty()) return;
                    for (Map<String,Object> bid : bids) {
                        String user   = str(bid,"username");
                        double amount = num(bid,"amount");
                        String time   = str(bid,"time");
                        if (time.length() > 16) time = time.substring(0,16);
                        historyList.getItems().add(0, user + "  →  " + formatVND(amount) + "   ["+time+"]");
                        bidCount++;
                        priceSeries.getData().add(new XYChart.Data<>("Lần "+bidCount, amount));
                        if (user.equals(myUsername)) {
                            myBidCountInRoom++;
                            myBidHistoryList.getItems().add(0, formatVND(amount)+"   ["+time+"]");
                            if (amount > myBestBid) myBestBid = amount;
                        }
                        lastWinner = user; currentPriceVal = amount;
                    }
                    lblWinner.setText("👤 Người dẫn đầu: " + lastWinner);
                    lblProfileBidCount.setText(String.valueOf(myBidCountInRoom));
                    if (myBestBid > 0) lblMyBestBid.setText("Cao nhất: " + formatVND(myBestBid));
                });
            } catch (Exception e) { System.err.println("BID_HISTORY err: " + e.getMessage()); }
        });

        ClientMain.registerListener("WON_AUCTIONS", payload -> {
            try {
                java.lang.reflect.Type lt =
                        new com.google.gson.reflect.TypeToken<List<Map<String,Object>>>(){}.getType();
                List<Map<String,Object>> won = gson.fromJson(payload, lt);
                int wins = (won != null) ? won.size() : 0;
                Platform.runLater(() -> {
                    lblProfileWins.setText(String.valueOf(wins));
                    int total = wins + myBidCountInRoom;
                    lblProfileWinRate.setText(total > 0 ? ((int)((double)wins/total*100))+"%": "--");
                    updateRankBadge(wins);
                });
            } catch (Exception e) { System.err.println("WON_AUCTIONS err: " + e.getMessage()); }
        });

        ClientMain.registerListener("UPDATE_PRICE", payload -> {
            String[] data = payload.split(":");
            if (data.length < 3 || !data[0].equals(currentRoomId)) return;
            Platform.runLater(() -> {
                currentPriceVal = Double.parseDouble(data[1]);
                lastWinner      = data[2];
                lblCurrentPrice.setText(formatVND(currentPriceVal));
                lblWinner.setText("👤 Người dẫn đầu: " + lastWinner);
                historyList.getItems().add(0, lastWinner + "  →  " + formatVND(currentPriceVal));
                bidCount++;
                priceSeries.getData().add(new XYChart.Data<>("Lần "+bidCount, currentPriceVal));
                if (lastWinner.equals(myUsername)) {
                    myBidCountInRoom++;
                    myBidHistoryList.getItems().add(0, formatVND(currentPriceVal));
                    if (currentPriceVal > myBestBid) {
                        myBestBid = currentPriceVal;
                        lblMyBestBid.setText("Cao nhất: " + formatVND(myBestBid));
                    }
                    lblProfileBidCount.setText(String.valueOf(myBidCountInRoom));
                    // Sync số dư thực từ server thay vì tự trừ phía client
                    ClientMain.send(gson.toJson(new MessageDTO("GET_BALANCE", "")));
                }
            });
        });

        ClientMain.registerListener("AUTO_BID_EXCEEDED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            Platform.runLater(() -> {
                deactivateAutoBidUI();
                showAlert(Alert.AlertType.INFORMATION, "AutoBid",
                        "AutoBid đã đạt giới hạn tối đa và tự dừng.");
            });
        });

        ClientMain.registerListener("AUCTION_FINISHED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            Platform.runLater(() -> {
                remainingSeconds = 0;
                lblTimer.setText("ĐÃ KẾT THÚC");
                btnPlaceBid.setDisable(true);
                btnOpenAutoBid.setDisable(true);
                if (timer != null) timer.cancel();
                overlayFinished.setVisible(true);
                deactivateAutoBidUI();
                if (myUsername != null && myUsername.equals(lastWinner)) {
                    lblFinishIcon.setText("🏆");
                    lblFinishTitle.setText("CHÚC MỪNG CHIẾN THẮNG!");
                    lblFinishTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #059669;");
                    lblFinishMessage.setText("Bạn đã đấu giá thành công với mức giá " + formatVND(currentPriceVal));
                } else {
                    lblFinishIcon.setText("🛑");
                    lblFinishTitle.setText("PHIÊN ĐẤU GIÁ KẾT THÚC");
                    lblFinishTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #EF4444;");
                    lblFinishMessage.setText("Sản phẩm đã thuộc về "
                            + (lastWinner.isEmpty() ? "người khác" : lastWinner));
                }
            });
        });

        // Admin hủy phòng khi đang ở trong — hiện overlay thông báo
        ClientMain.registerListener("AUCTION_CANCELED", payload -> {
            if (!payload.equals(currentRoomId)) return;
            Platform.runLater(() -> {
                if (timer != null) timer.cancel();
                btnPlaceBid.setDisable(true);
                btnOpenAutoBid.setDisable(true);
                deactivateAutoBidUI();
                updateStatusBadge("CANCELED");
                overlayFinished.setVisible(true);
                lblFinishIcon.setText("🚫");
                lblFinishTitle.setText("PHIÊN ĐẤU GIÁ BỊ HỦY");
                lblFinishTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #EF4444;");
                lblFinishMessage.setText("Quản trị viên đã hủy phiên đấu giá này.");
            });
        });

        ClientMain.registerListener("BID_FAILED", payload ->
                Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Đặt giá thất bại", payload))
        );

        // Server xác nhận bid thành công (server đã broadcast UPDATE_PRICE cho mọi người,
        // nhưng người đặt cần một feedback rõ ràng).
        ClientMain.registerListener("BID_SUCCESS", payload ->
                Platform.runLater(() -> {
                    // Hiển thị toast nhỏ, không block bằng showAndWait
                    showToast("✅ Đặt giá thành công!");
                })
        );

        // Bắt mọi lỗi chung từ server (chưa đăng nhập, hết quyền, lỗi DB, v.v.)
        ClientMain.registerListener("ERROR", payload ->
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Lỗi từ máy chủ", payload))
        );

        // Cập nhật số dư sau khi bid thành công (sync từ server)
        ClientMain.registerListener("BALANCE_DATA", payload -> {
            try {
                double newBalance = Double.parseDouble(payload.trim());
                UserSession.getInstance().setBalance(newBalance);
                Platform.runLater(() ->
                        lblProfileBalance.setText(formatVND(newBalance))
                );
            } catch (Exception ignored) {}
        });
    }

    public void setRoomId(String id) {
        this.currentRoomId = id;
        lblRoomId.setText("Phòng #" + id);
        ClientMain.send(gson.toJson(new MessageDTO("GET_AUCTION_DETAIL",    id)));
        ClientMain.send(gson.toJson(new MessageDTO("GET_BID_HISTORY",       id)));
        ClientMain.send(gson.toJson(new MessageDTO("GET_MY_WON_AUCTIONS",   "")));
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                Platform.runLater(() -> {
                    if (remainingSeconds > 0) {
                        remainingSeconds--;
                        int h=remainingSeconds/3600, m=(remainingSeconds%3600)/60, s=remainingSeconds%60;
                        lblTimer.setText(String.format("%02d:%02d:%02d",h,m,s));
                    } else {
                        lblTimer.setText("ĐANG CHỐT...");
                        timer.cancel();
                    }
                });
            }
        }, 1000, 1000);
    }

    @FXML
    void handlePlaceBid() {
        String text = txtBidAmount.getText().trim();
        if (text.isEmpty()) { showAlert(Alert.AlertType.WARNING,"Thiếu dữ liệu","Vui lòng nhập số tiền!"); return; }
        double amount;
        try { amount = Double.parseDouble(text); }
        catch (Exception e) { showAlert(Alert.AlertType.ERROR,"Lỗi","Số tiền không hợp lệ!"); return; }
        if (amount <= currentPriceVal) { showAlert(Alert.AlertType.WARNING,"Không hợp lệ","Giá đặt phải cao hơn giá hiện tại!"); return; }
        if (amount > UserSession.getInstance().getBalance()) { showAlert(Alert.AlertType.WARNING,"Số dư không đủ","Số dư: "+formatVND(UserSession.getInstance().getBalance())); return; }
        ClientMain.send(gson.toJson(new MessageDTO("BID", currentRoomId+":"+myUsername+":"+amount)));
        txtBidAmount.clear();
    }

    @FXML
    void handleQuickBid(ActionEvent event) {
        Button btn = (Button) event.getSource();
        double add  = Double.parseDouble(btn.getUserData().toString());
        txtBidAmount.setText(String.valueOf((long)(currentPriceVal + add)));
    }

    @FXML
    void handleOpenAutoBidDialog(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("../views/autobid-dialog.fxml"));
            Parent root = loader.load();
            AutoBidDialogController ctrl = loader.getController();
            ctrl.setup(currentRoomId, currentPriceVal, (mode, cfg) -> {
                // Đăng ký listener chờ response — chỉ activate UI khi server xác nhận thành công
                ClientMain.registerListener("SET_AUTO_BID_SUCCESS", payload -> {
                    ClientMain.unregisterListener("SET_AUTO_BID_SUCCESS");
                    ClientMain.unregisterListener("SET_AUTO_BID_FAILED");
                    Platform.runLater(() -> {
                        isAutoBidActive = true;
                        activateAutoBidUI(cfg);
                    });
                });

                ClientMain.registerListener("SET_AUTO_BID_FAILED", payload -> {
                    ClientMain.unregisterListener("SET_AUTO_BID_SUCCESS");
                    ClientMain.unregisterListener("SET_AUTO_BID_FAILED");
                    Platform.runLater(() ->
                            showAlert(Alert.AlertType.WARNING, "Kích hoạt AutoBid thất bại",
                                    "Server báo: " + payload)
                    );
                });

                ClientMain.send(gson.toJson(new MessageDTO("SET_AUTO_BID",
                        currentRoomId + ":" + cfg.maxBid + ":" + cfg.increment)));
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
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được AutoBid dialog: " + e.getMessage());
        }
    }

    private void activateAutoBidUI(AutoBidDialogController.AutoBidConfig cfg) {
        paneAutoBidActive.setVisible(true);
        paneAutoBidActive.setManaged(true);
        btnOpenAutoBid.setText("🤖  Đang bật");
        btnOpenAutoBid.setStyle("-fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-border-color: #6EE7B7; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 8 16;");
        String modeLabel = switch (cfg.mode) {
            case "FIXED" -> "Giá cố định"; case "SNIPE" -> "Snipe"; default -> "Tăng dần";
        };
        lblAutoBidInfo.setText("Giới hạn: "+formatVND(cfg.maxBid)
                +"  ·  Bước: "+formatVND(cfg.increment)+"  ·  Chế độ: "+modeLabel);
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
            Platform.runLater(() ->
                    showAlert(Alert.AlertType.WARNING, "Hủy AutoBid thất bại",
                            "Server báo: " + payload + "\nUI vẫn giữ trạng thái cũ.")
            );
        });

        ClientMain.send(gson.toJson(new MessageDTO("CANCEL_AUTO_BID", currentRoomId)));
    }

    private void deactivateAutoBidUI() {
        isAutoBidActive = false;
        paneAutoBidActive.setVisible(false);
        paneAutoBidActive.setManaged(false);
        btnOpenAutoBid.setText("🤖  Cài AutoBid");
        btnOpenAutoBid.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 8 16;");
    }

    private void updateStatusBadge(String status) {
        switch (status.toUpperCase()) {
            case "RUNNING" -> { lblStatusBadge.setText("● Đang chạy");
                lblStatusBadge.setStyle("-fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 20;"); }
            case "OPEN" -> { lblStatusBadge.setText("○ Sắp bắt đầu");
                lblStatusBadge.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 20;"); }
            default -> { lblStatusBadge.setText("■ Kết thúc");
                lblStatusBadge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #64748B; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 20;"); }
        }
    }

    @FXML
    void handleBackToList(ActionEvent event) {
        if (timer != null) timer.cancel();
        ClientMain.unregisterListener("AUCTION_DETAIL_DATA");
        ClientMain.unregisterListener("UPDATE_PRICE");
        ClientMain.unregisterListener("AUCTION_FINISHED");
        ClientMain.unregisterListener("AUCTION_CANCELED");
        ClientMain.unregisterListener("BID_FAILED");
        ClientMain.unregisterListener("BID_SUCCESS");
        ClientMain.unregisterListener("ERROR");
        ClientMain.unregisterListener("AUTO_BID_EXCEEDED");
        ClientMain.unregisterListener("BID_HISTORY");
        ClientMain.unregisterListener("WON_AUCTIONS");
        ClientMain.unregisterListener("BALANCE_DATA");
        ClientMain.unregisterListener("SET_AUTO_BID_SUCCESS");
        ClientMain.unregisterListener("SET_AUTO_BID_FAILED");
        ClientMain.unregisterListener("CANCEL_AUTO_BID_SUCCESS");
        ClientMain.unregisterListener("CANCEL_AUTO_BID_FAILED");
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/views/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String formatVND(double val) {
        return String.format("%,.0f đ", val).replace(',', '.');
    }
    private String str(Map<String,Object> m, String k) {
        return m.get(k) != null ? m.get(k).toString() : "";
    }
    private double num(Map<String,Object> m, String k) {
        try { return m.get(k) != null ? ((Number)m.get(k)).doubleValue() : 0; }
        catch (Exception e) { return 0; }
    }
    private void showAlert(Alert.AlertType t, String title, String content) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null);
        a.setContentText(content); a.showAndWait();
    }

    /**
     * Hiển thị toast notification ngắn (3 giây), không block UI.
     * Dùng cho các phản hồi nhanh như BID_SUCCESS — không nên dùng dialog modal.
     */
    private void showToast(String message) {
        try {
            javafx.stage.Window window = lblCurrentPrice.getScene().getWindow();
            if (window == null) return;

            javafx.stage.Popup popup = new javafx.stage.Popup();
            Label toast = new Label(message);
            toast.setStyle(
                    "-fx-background-color: rgba(16, 185, 129, 0.95);" +
                            "-fx-text-fill: white;" +
                            "-fx-padding: 12 24;" +
                            "-fx-background-radius: 10;" +
                            "-fx-font-size: 14px;" +
                            "-fx-font-weight: bold;"
            );
            popup.getContent().add(toast);
            popup.setAutoHide(true);

            // Đặt toast ở góc dưới-phải cửa sổ
            double x = window.getX() + window.getWidth()  - 280;
            double y = window.getY() + window.getHeight() - 100;
            popup.show(window, x, y);

            // Tự đóng sau 2.5 giây
            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
            delay.setOnFinished(e -> popup.hide());
            delay.play();
        } catch (Exception ignored) {
            // Nếu vì lý do gì không show được toast, im lặng — đã có UPDATE_PRICE feedback rồi
        }
    }
}