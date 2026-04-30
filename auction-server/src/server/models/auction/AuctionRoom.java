// Đường dẫn: auction-server/src/server/models/auction/AuctionRoom.java
package server.models.auction;

import server.exceptions.InvalidBidException;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.User;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AuctionRoom implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private Item item;
    private int itemID;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private User currentWinner;

    private List<BidMessage> bidHistory;
    // THÊM MỚI 3.2.1: Danh sách những người đăng ký Auto-bid trong phòng này
    private List<AutoBidConfig> autoBidders;

    private LocalDateTime starttime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    public AuctionRoom(int id, Item item, LocalDateTime starttime, LocalDateTime endTime) {
        this.id = id;
        this.item = item;
        this.itemID = item.getItemId();
        this.startPrice = item.getStartingPrice();
        this.bidHistory = new ArrayList<>();
        this.autoBidders = new ArrayList<>(); // Khởi tạo danh sách auto-bid
        this.starttime = starttime;
        this.endTime = endTime;

        if (LocalDateTime.now().isBefore(starttime)) {
            this.status = AuctionStatus.OPEN;
        } else {
            this.status = AuctionStatus.RUNNING;
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endTime);
    }

    // ================== LOGIC TẠO AUTO-BID (3.2.1) ==================
    public synchronized void registerAutoBid(Bidder bidder, BigDecimal maxBid, BigDecimal increment) throws InvalidBidException {
        if (this.status != AuctionStatus.RUNNING) {
            throw new InvalidBidException("Không thể cài đặt Auto-bid do phòng chưa mở hoặc đã đóng!");
        }
        if (bidder.getBalance().compareTo(maxBid) < 0) {
            throw new InvalidBidException("Số dư của bạn không đủ để thiết lập mức Max Bid này!");
        }

        this.autoBidders.add(new AutoBidConfig(bidder, maxBid, increment));
        System.out.println(">>> [Hệ thống] " + bidder.getUsername() + " đã cài đặt Auto-Bid (Max: " + maxBid + ", Bước giá: " + increment + ")");

        // Kích hoạt quét thử xem có thể nhảy giá ngay lập tức không
        processAutoBids();
    }

    // ================== LOGIC ĐẶT GIÁ THỦ CÔNG (3.1.3 & 3.1.5) ==================
    // VÁ LỖI 3.1.5: Sử dụng throws InvalidBidException thay vì Exception thường
    public synchronized void placeBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {

        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            this.status = AuctionStatus.FINISHED;
            throw new InvalidBidException("Lỗi: Phiên đấu giá đã kết thúc hoặc chưa mở!");
        }

        BigDecimal priceToBeat = (this.currentPrice != null) ? this.currentPrice : this.startPrice;
        if (amount.compareTo(priceToBeat) <= 0) {
            throw new InvalidBidException("Lỗi: Giá đặt (" + amount + ") phải cao hơn mức giá hiện tại (" + priceToBeat + ")!");
        }

        if (bidder.getBalance().compareTo(amount) < 0) {
            throw new InvalidBidException("Lỗi: Số dư tài khoản không đủ để đặt mức giá này!");
        }

        // Cập nhật người dẫn đầu
        this.currentPrice = amount;
        this.currentWinner = bidder;
        System.out.println(">>> [Manual Bid] " + bidder.getUsername() + " đặt giá thủ công: " + amount);

        // Kích hoạt thuật toán Anti-sniping
        triggerAntiSniping();

        // THÊM MỚI 3.2.1: Sau khi có người đặt thủ công, đánh thức các Auto-bidder
        processAutoBids();
    }

    // ================== THUẬT TOÁN ĐẤU TRƯỜNG AUTO-BID (3.2.1) ==================
    private void processAutoBids() {
        if (autoBidders.isEmpty()) return;

        // Ưu tiên người đăng ký Auto-bid trước (ai đến trước phục vụ trước)
        autoBidders.sort(Comparator.comparing(AutoBidConfig::getRegisterTime));

        boolean newBidPlaced = true;

        // Vòng lặp Battle: Cho các Auto-bidder nâng giá chéo nhau đến khi chạm kịch kim (MaxBid)
        while (newBidPlaced) {
            newBidPlaced = false;

            for (AutoBidConfig config : autoBidders) {
                // Bỏ qua nếu người này đang là người dẫn đầu (không tự đấu giá với chính mình)
                if (currentWinner != null && config.getBidder().getUserId() == currentWinner.getUserId()) {
                    continue;
                }

                BigDecimal priceToBeat = (this.currentPrice != null) ? this.currentPrice : this.startPrice;
                BigDecimal nextBid = priceToBeat.add(config.getIncrement()); // Cộng thêm bước giá

                // Điều kiện để Auto-bid thành công:
                // 1. Giá tiếp theo không vượt quá MaxBid của họ
                // 2. Họ có đủ tiền trong tài khoản
                if (nextBid.compareTo(config.getMaxBid()) <= 0 &&
                        config.getBidder().getBalance().compareTo(nextBid) >= 0) {

                    this.currentPrice = nextBid;
                    this.currentWinner = config.getBidder();
                    System.out.println("    [Auto-Bid] " + config.getBidder().getUsername() + " tự động nâng giá lên: " + nextBid);

                    triggerAntiSniping(); // Tự động trả giá cũng kích hoạt chống sniping

                    newBidPlaced = true;
                    break; // Ngắt vòng For để quay lại vòng While, cho phép người khác "phản đòn"
                }
            }
        }
    }

    // Tách riêng logic Anti-Sniping cho sạch code
    private void triggerAntiSniping() {
        long secondsLeft = Duration.between(LocalDateTime.now(), this.endTime).getSeconds();
        if (secondsLeft > 0 && secondsLeft <= 30) {
            this.endTime = this.endTime.plusSeconds(60);
            System.out.println(">>> [Anti-sniping] Có biến động giá phút chót! Phiên đấu giá gia hạn thêm 60 giây.");
        }
    }

    // ================= GETTER VÀ SETTER (GIỮ NGUYÊN) =================
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public BigDecimal getStartPrice() { return startPrice; }
    public void setStartPrice(BigDecimal startprice) { this.startPrice = startprice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public User getCurrentWinner() { return currentWinner; }
    public void setCurrentWinner(User currentWinner) { this.currentWinner = currentWinner; }
    public List<BidMessage> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidMessage> bidHistory) { this.bidHistory = bidHistory; }
    public LocalDateTime getStarttime() { return starttime; }
    public void setStarttime(LocalDateTime startime) { this.starttime = startime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }
}