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
import java.util.List;

/**
 * AuctionRoom — đơn vị trung tâm của một phiên đấu giá.
 *
 * Quyết định thiết kế:
 *   - placeBid() chỉ xử lý manual bid và Anti-sniping, KHÔNG tự kích hoạt auto-bid
 *     trong chính class này. Toàn bộ logic auto-bid được uỷ quyền cho AuctionService
 *     (tầng Service duy nhất có quyền ghi DB và broadcast socket), tránh duplicate và
 *     race condition giữa 2 luồng xử lý auto-bid.
 *   - registerAutoBid() chỉ validate và lưu config vào danh sách in-memory; AuctionService
 *     sẽ đọc danh sách này qua autoBidDAO khi cần.
 */
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
    // Dùng cho unit test (in-memory auto-bid, không persist DB)
    private List<AutoBidConfig> inMemoryAutoBidders;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private int extensionCount = 0;
    private static final int MAX_EXTENSIONS = 5;
    // Ngưỡng giây cuối để kích hoạt Anti-sniping
    private static final int ANTI_SNIPE_THRESHOLD_SECONDS = 30;
    // Số giây gia hạn mỗi lần Anti-sniping
    private static final int ANTI_SNIPE_EXTENSION_SECONDS = 60;

    public AuctionRoom() {}

    public AuctionRoom(int id, int sellerID, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        this.id        = id;
        this.sellerID  = sellerID;
        this.item      = item;
        this.itemID    = item.getItemId();
        this.startPrice = item.getStartingPrice();
        this.currentPrice = this.startPrice; // tránh NullPointerException khi chưa có ai bid
        this.bidHistory = new ArrayList<>();
        this.inMemoryAutoBidders = new ArrayList<>();
        this.startTime  = startTime;
        this.endTime    = endTime;
        this.status     = LocalDateTime.now().isBefore(startTime)
                ? AuctionStatus.OPEN
                : AuctionStatus.RUNNING;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endTime);
    }

    /**
     * Đặt giá thủ công.
     * Chỉ validate + cập nhật state in-memory + kích hoạt Anti-sniping.
     * KHÔNG tự kích hoạt auto-bid — AuctionService sẽ làm sau khi hàm này trả về.
     */
    public synchronized void placeBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        LocalDateTime serverNow = LocalDateTime.now();

        if (this.status != AuctionStatus.RUNNING || serverNow.isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            throw new InvalidBidException(
                    "Từ chối: Phiên đấu giá đã kết thúc! (Server Time: " + serverNow.format(fmt) + ")");
        }

        if (amount.compareTo(this.currentPrice) <= 0) {
            throw new InvalidBidException("Từ chối: Giá đặt phải lớn hơn " + this.currentPrice);
        }

        if (bidder.getBalance().compareTo(amount) < 0) {
            throw new InvalidBidException("Từ chối: Tài khoản không đủ số dư!");
        }

        applyNewWinner(bidder, amount, "Manual Bid");
    }

    /**
     * Đăng ký Auto-bid cho unit test và logic in-memory.
     * Trong production flow, AuctionService đọc config từ DB qua AutoBidDAO.
     * Phương thức này giữ lại để AuctionRoomTest có thể kiểm thử thuật toán
     * auto-bid (tie-breaker, ALL-IN, war) mà không cần DB.
     */
    public synchronized void registerAutoBid(Bidder bidder, BigDecimal maxBid, BigDecimal increment)
            throws InvalidBidException {
        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            throw new InvalidBidException("Không thể thiết lập Auto-bid lúc này!");
        }
        if (bidder.getBalance().compareTo(maxBid) < 0) {
            throw new InvalidBidException("Số dư không đủ để bảo lãnh mức Max Bid này!");
        }
        if (inMemoryAutoBidders == null) {
            inMemoryAutoBidders = new java.util.ArrayList<>();
        }
        AutoBidConfig config = new AutoBidConfig(this, bidder, maxBid, increment);
        inMemoryAutoBidders.add(config);
        System.out.println(">>> [Auto-Bid] " + bidder.getUsername()
                + " kích hoạt: Max=" + maxBid + ", Bước=" + increment);
        processInMemoryAutoBids();
    }

    /** Xử lý auto-bid in-memory (chỉ dùng cho unit test, không ghi DB). */
    private void processInMemoryAutoBids() {
        if (inMemoryAutoBidders == null || inMemoryAutoBidders.isEmpty()) return;
        inMemoryAutoBidders.sort(java.util.Comparator.comparing(AutoBidConfig::getRegisterTime));

        boolean newBidPlaced;
        do {
            newBidPlaced = false;
            for (AutoBidConfig config : inMemoryAutoBidders) {
                if (currentWinner != null
                        && currentWinner.getUserId() == config.getBidder().getUserId()) continue;

                BigDecimal next = this.currentPrice.add(config.getIncrement());

                if (next.compareTo(config.getMaxBid()) <= 0
                        && config.getBidder().getBalance().compareTo(next) >= 0) {
                    applyNewWinner(config.getBidder(), next, "Auto-Bid Step");
                    newBidPlaced = true;
                    break;
                } else {
                    boolean canWin = config.getMaxBid().compareTo(this.currentPrice) > 0;
                    boolean tieBreak = config.getMaxBid().compareTo(this.currentPrice) == 0
                            && isOlderThanCurrentWinner(config);
                    if ((canWin || tieBreak)
                            && config.getBidder().getBalance().compareTo(config.getMaxBid()) >= 0) {
                        applyNewWinner(config.getBidder(), config.getMaxBid(), "Auto-Bid ALL-IN");
                        newBidPlaced = true;
                        break;
                    }
                }
            }
        } while (newBidPlaced);
    }

    private boolean isOlderThanCurrentWinner(AutoBidConfig challenger) {
        if (currentWinner == null || inMemoryAutoBidders == null) return true;
        for (AutoBidConfig c : inMemoryAutoBidders) {
            if (c.getBidder().getUserId() == currentWinner.getUserId()) {
                return challenger.getRegisterTime().isBefore(c.getRegisterTime());
            }
        }
        return true; // winner là manual bidder → auto-bidder luôn được tiebreak
    }

    /**
     * Đặt giá từ hệ thống Auto-bid (được AuctionService gọi).
     * Không kiểm tra balance ở đây vì AuctionService đã kiểm tra trước.
     */
    public synchronized void placeAutoBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            throw new InvalidBidException("Phiên đã kết thúc, không thể auto-bid.");
        }
        if (amount.compareTo(this.currentPrice) <= 0) {
            throw new InvalidBidException("Auto-bid thấp hơn giá hiện tại, bỏ qua.");
        }
        applyNewWinner(bidder, amount, "Auto-Bid");
    }

    private void applyNewWinner(Bidder bidder, BigDecimal amount, String logType) {
        this.currentPrice = amount;
        this.currentWinner = bidder;
        this.bidHistory.add(new BidMessage(0, bidder.getUserId(), this.id, amount));
        System.out.println("    [" + logType + "] " + bidder.getUsername() + " vươn lên với giá: " + amount);
        triggerAntiSniping();
    }

    /**
     * Anti-sniping: nếu bid mới xảy ra trong ANTI_SNIPE_THRESHOLD_SECONDS giây cuối,
     * gia hạn thêm ANTI_SNIPE_EXTENSION_SECONDS giây tính từ thời điểm bid (now),
     * tối đa MAX_EXTENSIONS lần.
     */
    private void triggerAntiSniping() {
        if (extensionCount >= MAX_EXTENSIONS) return;

        LocalDateTime now = LocalDateTime.now();
        // Phòng chưa hết giờ nhưng còn ≤ ngưỡng giây cuối
        if (!now.isAfter(this.endTime)
                && !this.endTime.isAfter(now.plusSeconds(ANTI_SNIPE_THRESHOLD_SECONDS))) {
            // Gia hạn từ now để đảm bảo luôn còn ít nhất EXTENSION giây
            this.endTime = now.plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS);
            extensionCount++;
            System.out.println(">>> [Anti-sniping] Gia hạn lần " + extensionCount
                    + ": endTime mới = " + this.endTime);
        }
    }

    // ================= GETTER VÀ SETTER =================
    public int getId()                                  { return id; }
    public void setId(int id)                          { this.id = id; }
    public int getSellerID()                           { return sellerID; }
    public void setSellerID(int sellerID)              { this.sellerID = sellerID; }
    public Item getItem()                              { return item; }
    public void setItem(Item item)                     { this.item = item; }
    public BigDecimal getStartPrice()                  { return startPrice; }
    public void setStartPrice(BigDecimal startPrice)   { this.startPrice = startPrice; }
    public BigDecimal getCurrentPrice()                { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public User getCurrentWinner()                     { return currentWinner; }
    public void setCurrentWinner(User currentWinner)   { this.currentWinner = currentWinner; }
    public List<BidMessage> getBidHistory()            { return bidHistory; }
    public void setBidHistory(List<BidMessage> bids)   { this.bidHistory = bids; }
    public LocalDateTime getStarttime()                { return startTime; }
    public void setStarttime(LocalDateTime startTime)  { this.startTime = startTime; }
    public LocalDateTime getEndTime()                  { return endTime; }
    public void setEndTime(LocalDateTime endTime)      { this.endTime = endTime; }
    public AuctionStatus getStatus()                   { return status; }
    public void setStatus(AuctionStatus status)        { this.status = status; }
    public int getItemID()                             { return itemID; }
}