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
import java.time.format.DateTimeFormatter;
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
    private List<AutoBidConfig> autoBidders;

    private LocalDateTime starttime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    // FIX CASE 4: Chống lạm dụng gia hạn vô tận (Anti-sniping limit)
    private int extensionCount = 0;
    private static final int MAX_EXTENSIONS = 5;
    public AuctionRoom() {
    }

    public AuctionRoom(int id, Item item, LocalDateTime starttime, LocalDateTime endTime) {
        this.id = id;
        this.item = item;
        this.itemID = item.getItemId();
        this.startPrice = item.getStartingPrice();
        this.bidHistory = new ArrayList<>();
        this.autoBidders = new ArrayList<>();
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

    // =========================================================================
    // 1. LOGIC ĐẶT GIÁ THỦ CÔNG (Bảo mật Timing & Server-side check)
    // =========================================================================
    public synchronized void placeBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        // FIX CASE 9: Lấy thời gian chuẩn của Server tại khoảnh khắc nhận request để đối chiếu
        LocalDateTime serverNow = LocalDateTime.now();

        if (this.status != AuctionStatus.RUNNING || serverNow.isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            throw new InvalidBidException("Từ chối: Phiên đấu giá đã kết thúc! (Server Time: " + serverNow.format(fmt) + ")");
        }

        BigDecimal priceToBeat = (this.currentPrice != null) ? this.currentPrice : this.startPrice;
        if (amount.compareTo(priceToBeat) <= 0) {
            throw new InvalidBidException("Từ chối: Giá đặt phải lớn hơn " + priceToBeat);
        }

        if (bidder.getBalance().compareTo(amount) < 0) {
            throw new InvalidBidException("Từ chối: Tài khoản không đủ số dư!");
        }

        // Chốt giá thủ công
        applyNewWinner(bidder, amount, "Manual Bid");

        // Đánh thức hệ thống Đấu trường Auto-Bid
        processAutoBids();
    }

    // =========================================================================
    // 2. LOGIC ĐĂNG KÝ AUTO-BID
    // =========================================================================
    public synchronized void registerAutoBid(Bidder bidder, BigDecimal maxBid, BigDecimal increment) throws InvalidBidException {
        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            throw new InvalidBidException("Không thể thiết lập Auto-bid lúc này!");
        }
        if (bidder.getBalance().compareTo(maxBid) < 0) {
            throw new InvalidBidException("Số dư không đủ để bảo lãnh mức Max Bid này!");
        }

        this.autoBidders.add(new AutoBidConfig());
        System.out.println(">>> [Auto-Bid] " + bidder.getUsername() + " kích hoạt: Max=" + maxBid + ", Bước=" + increment);

        // Ngay khi đăng ký, quét xem có thể đè giá ngay lập tức không
        processAutoBids();
    }

    // =========================================================================
    // 3. THUẬT TOÁN ĐẤU TRƯỜNG AUTO-BID (Fix Case 6, 7, 8)
    // =========================================================================
    private void processAutoBids() {
        if (autoBidders.isEmpty()) return;

        // Ưu tiên theo thời gian đăng ký (Tie-breaker cho Case 7)
        autoBidders.sort(Comparator.comparing(AutoBidConfig::getRegisterTime));

        boolean newBidPlaced;
        do {
            newBidPlaced = false;

            for (AutoBidConfig config : autoBidders) {
                // Bỏ qua nếu đang là top 1
                if (currentWinner != null && currentWinner.getUserId() == config.getBidder().getUserId()) {
                    continue;
                }

                BigDecimal priceToBeat = (this.currentPrice != null) ? this.currentPrice : this.startPrice;
                BigDecimal nextNormalBid = priceToBeat.add(config.getIncrement());

                // Kịch bản A: Nâng giá bình thường theo đúng increment
                if (nextNormalBid.compareTo(config.getMaxBid()) <= 0 && config.getBidder().getBalance().compareTo(nextNormalBid) >= 0) {
                    applyNewWinner(config.getBidder(), nextNormalBid, "Auto-Bid Step");
                    newBidPlaced = true;
                    break; // Phá vòng lặp for, bắt đầu lại do-while để người khác phản đòn
                }

                // FIX CASE 6 & 7 (Kịch bản B: Đánh Tất Tay - ALL IN)
                // Xảy ra khi bước giá tiếp theo vượt quá MaxBid, NHƯNG MaxBid vẫn có thể lật kèo
                else {
                    boolean canWinWithMax = config.getMaxBid().compareTo(priceToBeat) > 0;
                    boolean canStealTieBreaker = (config.getMaxBid().compareTo(priceToBeat) == 0) && isOlderThanCurrentWinner(config);

                    if ((canWinWithMax || canStealTieBreaker) && config.getBidder().getBalance().compareTo(config.getMaxBid()) >= 0) {
                        applyNewWinner(config.getBidder(), config.getMaxBid(), "Auto-Bid ALL-IN");
                        newBidPlaced = true;
                        break;
                    }
                }
            }
        } while (newBidPlaced);
    }

    // =========================================================================
    // CÁC HÀM PHỤ TRỢ (HELPERS)
    // =========================================================================

    private void applyNewWinner(Bidder bidder, BigDecimal amount, String logType) {
        this.currentPrice = amount;
        this.currentWinner = bidder;
        System.out.println("    [" + logType + "] " + bidder.getUsername() + " vươn lên với giá: " + amount);
        triggerAntiSniping();
    }

    // FIX CASE 4: Giới hạn Anti-Sniping
    private void triggerAntiSniping() {
        long secondsLeft = Duration.between(LocalDateTime.now(), this.endTime).getSeconds();
        if (secondsLeft > 0 && secondsLeft <= 30) {
            if (extensionCount < MAX_EXTENSIONS) {
                this.endTime = this.endTime.plusSeconds(60);
                extensionCount++;
                System.out.println(">>> [Anti-sniping] Gia hạn lần " + extensionCount + "/" + MAX_EXTENSIONS + " thêm 60 giây.");
            } else {
                System.out.println(">>> [Anti-sniping] Bỏ qua gia hạn (Đã chạm ngưỡng tối đa " + MAX_EXTENSIONS + " lần).");
            }
        }
    }

    // Hàm Tie-breaker: Kiểm tra xem người này có đăng ký Auto-bid trước người đang dẫn đầu không
    private boolean isOlderThanCurrentWinner(AutoBidConfig challenger) {
        if (currentWinner == null) return true;
        for (AutoBidConfig config : autoBidders) {
            if (config.getBidder().getUserId() == currentWinner.getUserId()) {
                return challenger.getRegisterTime().isBefore(config.getRegisterTime());
            }
        }
        return false;
    }

    // ================= GETTER VÀ SETTER =================
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