package server.models.auction;

import server.exceptions.InvalidBidException;
import server.models.items.Item;
import server.models.users.Bidder;
import server.models.users.User;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AuctionRoom implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int sellerID;
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

    private int extensionCount = 0;
    private static final int MAX_EXTENSIONS = 5;

    public AuctionRoom() {}

    public AuctionRoom(int id, int sellerID, Item item, LocalDateTime starttime, LocalDateTime endTime) {
        this.id = id;
        this.sellerID = sellerID;
        this.item = item;
        this.itemID = item.getItemId();
        this.startPrice = item.getStartingPrice();

        // [FIX 1] Lỗi chính: Init currentPrice bằng giá sàn ngay từ đầu để tránh lỗi Fail NullPointer.
        this.currentPrice = this.startPrice;

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

    public synchronized void placeBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        LocalDateTime serverNow = LocalDateTime.now();

        if (this.status != AuctionStatus.RUNNING || serverNow.isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            throw new InvalidBidException("Từ chối: Phiên đấu giá đã kết thúc! (Server Time: " + serverNow.format(fmt) + ")");
        }

        BigDecimal priceToBeat = this.currentPrice; // An toàn tuyệt đối vì đã init
        if (amount.compareTo(priceToBeat) <= 0) {
            throw new InvalidBidException("Từ chối: Giá đặt phải lớn hơn " + priceToBeat);
        }

        if (bidder.getBalance().compareTo(amount) < 0) {
            throw new InvalidBidException("Từ chối: Tài khoản không đủ số dư!");
        }

        applyNewWinner(bidder, amount, "Manual Bid");
        processAutoBids();
    }

    public synchronized void registerAutoBid(Bidder bidder, BigDecimal maxBid, BigDecimal increment) throws InvalidBidException {
        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            throw new InvalidBidException("Không thể thiết lập Auto-bid lúc này!");
        }
        if (bidder.getBalance().compareTo(maxBid) < 0) {
            throw new InvalidBidException("Số dư không đủ để bảo lãnh mức Max Bid này!");
        }

        AutoBidConfig config = new AutoBidConfig(this, bidder, maxBid, increment);
        this.autoBidders.add(config);
        System.out.println(">>> [Auto-Bid] " + bidder.getUsername() + " kích hoạt: Max=" + maxBid + ", Bước=" + increment);
        processAutoBids();
    }

    private void processAutoBids() {
        if (autoBidders.isEmpty()) return;
        autoBidders.sort(Comparator.comparing(AutoBidConfig::getRegisterTime));

        boolean newBidPlaced;
        do {
            newBidPlaced = false;
            for (AutoBidConfig config : autoBidders) {
                if (currentWinner != null && currentWinner.getUserId() == config.getBidder().getUserId()) continue;

                BigDecimal priceToBeat = this.currentPrice;
                BigDecimal nextNormalBid = priceToBeat.add(config.getIncrement());

                if (nextNormalBid.compareTo(config.getMaxBid()) <= 0 && config.getBidder().getBalance().compareTo(nextNormalBid) >= 0) {
                    applyNewWinner(config.getBidder(), nextNormalBid, "Auto-Bid Step");
                    newBidPlaced = true;
                    break;
                } else {
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

    private void applyNewWinner(Bidder bidder, BigDecimal amount, String logType) {
        this.currentPrice = amount;
        this.currentWinner = bidder;

        // [FIX 3] Thiếu bidHistory: Lưu lại mọi cú bid vào List để pass test size và lưu DB sau này.
        this.bidHistory.add(new BidMessage(0, bidder.getUserId(), this.id, amount));

        System.out.println("    [" + logType + "] " + bidder.getUsername() + " vươn lên với giá: " + amount);
        triggerAntiSniping();
    }

    private void triggerAntiSniping() {
        LocalDateTime now = LocalDateTime.now();
        if (!now.isAfter(this.endTime)) {
            // [FIX 4] Fail Random do sai số mili-giây: Sử dụng "plusSeconds" kết hợp "isAfter"
            // đảm bảo chính xác tuyệt đối thay vì đếm số giây (Duration) dễ bị làm tròn xuống.
            if (!this.endTime.isAfter(now.plusSeconds(30))) {
                if (extensionCount < MAX_EXTENSIONS) {
                    this.endTime = this.endTime.plusSeconds(60);
                    extensionCount++;
                    System.out.println(">>> [Anti-sniping] Gia hạn lần " + extensionCount + " thêm 60 giây.");
                }
            }
        }
    }

    private boolean isOlderThanCurrentWinner(AutoBidConfig challenger) {
        if (currentWinner == null) return true;
        for (AutoBidConfig config : autoBidders) {
            if (config.getBidder().getUserId() == currentWinner.getUserId()) {
                return challenger.getRegisterTime().isBefore(config.getRegisterTime());
            }
        }
        // [FIX 2] Sai Tie-breaker: Nếu winner hiện tại KHÔNG PHẢI là Auto-bidder (mà là 1 Manual Bidder đánh thủ công),
        // thì Auto-bidder luôn được tính là "đăng ký trước" và sẽ chiếm quyền Tie-break.
        return true;
    }

    // ================= GETTER VÀ SETTER =================
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSellerID() { return sellerID; }
    public void setSellerID(int sellerID) { this.sellerID = sellerID; }
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