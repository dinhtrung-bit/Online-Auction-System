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
 * AuctionRoom — domain model của một phiên đấu giá.
 *
 * Thay đổi so với phiên bản cũ:
 *   - Xóa toàn bộ inMemoryAutoBidders: AuctionService dùng AutoBidDAO (DB-backed),
 *     nên danh sách trong RAM này không bao giờ được đọc trong production.
 *   - Xóa registerAutoBid(), placeAutoBid(), processInMemoryAutoBids(),
 *     isOlderThanCurrentWinner() — toàn bộ auto-bid logic thuộc về AuctionService.
 *   - Giữ lại placeBid() vì đây là domain invariant thực sự: phòng tự bảo vệ
 *     trạng thái của mình, service không được bypass.
 *   - Giữ anti-sniping trong applyNewWinner() vì đây là rule của phòng, không phải service.
 */
public class AuctionRoom implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int MAX_EXTENSIONS              = 5;
    private static final int ANTI_SNIPE_THRESHOLD_SECS  = 30;
    private static final int ANTI_SNIPE_EXTENSION_SECS  = 60;

    private int id;
    private int sellerID;
    private Item item;
    private int itemID;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private User currentWinner;
    private List<BidMessage> bidHistory;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private int extensionCount = 0;

    /** Constructor rỗng cho DAO. */
    public AuctionRoom() {
        this.bidHistory   = new ArrayList<>();
        this.status       = AuctionStatus.OPEN;
        this.currentPrice = BigDecimal.ZERO;
        this.startPrice   = BigDecimal.ZERO;
    }

    /**
     * Constructor đầy đủ — dùng khi tạo phiên mới từ AuctionService.
     * Tự thiết lập trạng thái OPEN hoặc RUNNING tuỳ thời điểm tạo.
     */
    public AuctionRoom(int id, int sellerID, Item item,
                       LocalDateTime startTime, LocalDateTime endTime) {
        if (item == null) {
            throw new IllegalArgumentException("AuctionRoom phải có item.");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian phiên đấu giá không hợp lệ.");
        }

        this.id          = id;
        this.sellerID    = sellerID;
        this.item        = item;
        this.itemID      = item.getItemId();
        this.startPrice  = item.getStartingPrice() != null ? item.getStartingPrice() : BigDecimal.ZERO;
        this.currentPrice = this.startPrice;
        this.bidHistory   = new ArrayList<>();
        this.startTime   = startTime;
        this.endTime     = endTime;
        this.status      = LocalDateTime.now().isBefore(startTime)
                ? AuctionStatus.OPEN
                : AuctionStatus.RUNNING;
    }

    // ── Domain invariants ────────────────────────────────────────────────────

    /**
     * Đặt giá thủ công. Phương thức này tự kiểm tra toàn bộ điều kiện hợp lệ
     * của phòng — AuctionService chỉ cần gọi rồi persist.
     *
     * @throws InvalidBidException khi vi phạm bất kỳ quy tắc nào của phòng
     */
    public synchronized void placeBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        requireValidBidderAndAmount(bidder, amount);

        if (status != AuctionStatus.RUNNING || isExpired()) {
            status = AuctionStatus.FINISHED;
            throw new InvalidBidException(
                    "Phiên đấu giá đã kết thúc! [" +
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + "]");
        }

        BigDecimal base = safeCurrentPrice();
        if (amount.compareTo(base) <= 0) {
            throw new InvalidBidException("Giá đặt phải lớn hơn giá hiện tại: " + base.toPlainString());
        }

        if (!bidder.canPlaceBid(amount)) {
            throw new InvalidBidException("Số dư tài khoản không đủ!");
        }

        applyBid(bidder, amount, "Manual");
    }

    /**
     * chủ yếu là để thực heienj cập thật bid thật vào auction
     * validate trước (freshBidder từ DB, nextBid đã tính sẵn).
     * Vẫn giữ synchronized và kiểm tra trạng thái để đảm bảo thread-safety.
     */
    public synchronized void applyAutoBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        requireValidBidderAndAmount(bidder, amount);

        if (status != AuctionStatus.RUNNING || isExpired()) {
            throw new InvalidBidException("Phiên đã kết thúc, không thể auto-bid.");
        }
        if (amount.compareTo(safeCurrentPrice()) <= 0) {
            throw new InvalidBidException("Auto-bid thấp hơn hoặc bằng giá hiện tại, bỏ qua.");
        }

        applyBid(bidder, amount, "Auto");
    }

    /** Kiểm tra phiên đã hết thời gian chưa. */
    public boolean isExpired() {
        return endTime != null && LocalDateTime.now().isAfter(endTime);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void applyBid(Bidder bidder, BigDecimal amount, String type) {
        this.currentPrice  = amount;
        this.currentWinner = bidder;
        this.bidHistory.add(new BidMessage(this.id, bidder.getUserId(), amount));

        System.out.printf("    [%s Bid] %s → %s%n", type, bidder.getUsername(), amount.toPlainString());
        triggerAntiSniping();
    }

    private void triggerAntiSniping() {
        if (extensionCount >= MAX_EXTENSIONS || endTime == null) return;

        LocalDateTime now = LocalDateTime.now();
        if (!now.isAfter(endTime) &&
                !endTime.isAfter(now.plusSeconds(ANTI_SNIPE_THRESHOLD_SECS))) {

            endTime = now.plusSeconds(ANTI_SNIPE_EXTENSION_SECS);
            extensionCount++;
            System.out.printf(">>> [Anti-snipe] Gia hạn #%d → endTime: %s%n", extensionCount, endTime);
        }
    }

    private void requireValidBidderAndAmount(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        if (bidder == null) throw new InvalidBidException("Bidder không hợp lệ.");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidBidException("Số tiền đặt giá phải lớn hơn 0.");
    }

    private BigDecimal safeCurrentPrice() {
        return currentPrice != null ? currentPrice : BigDecimal.ZERO;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getId()                      { return id; }
    public void setId(int id)               { this.id = id; }

    public int getSellerID()                { return sellerID; }
    public void setSellerID(int sellerID)   { this.sellerID = sellerID; }

    public Item getItem()                   { return item; }
    public void setItem(Item item) {
        this.item = item;
        if (item != null) {
            this.itemID = item.getItemId();
            if (startPrice == null)   startPrice   = item.getStartingPrice();
            if (currentPrice == null) currentPrice = item.getStartingPrice();
        }
    }

    public int getItemID()                  { return itemID; }
    public void setItemID(int itemID)       { this.itemID = itemID; }

    public BigDecimal getStartPrice() {
        return startPrice != null ? startPrice : BigDecimal.ZERO;
    }
    public void setStartPrice(BigDecimal p) { startPrice = p != null ? p : BigDecimal.ZERO; }

    public BigDecimal getCurrentPrice() {
        return currentPrice != null ? currentPrice : BigDecimal.ZERO;
    }
    public void setCurrentPrice(BigDecimal p) { currentPrice = p != null ? p : BigDecimal.ZERO; }

    public User getCurrentWinner()                  { return currentWinner; }
    public void setCurrentWinner(User winner)       { this.currentWinner = winner; }

    public List<BidMessage> getBidHistory() {
        if (bidHistory == null) bidHistory = new ArrayList<>();
        return bidHistory;
    }
    public void setBidHistory(List<BidMessage> bids) {
        this.bidHistory = bids != null ? bids : new ArrayList<>();
    }

    public LocalDateTime getStarttime()             { return startTime; }
    public void setStarttime(LocalDateTime t)       { this.startTime = t; }

    public LocalDateTime getEndTime()               { return endTime; }
    public void setEndTime(LocalDateTime t)         { this.endTime = t; }

    public AuctionStatus getStatus() {
        return status != null ? status : AuctionStatus.OPEN;
    }
    public void setStatus(AuctionStatus s)          { this.status = s != null ? s : AuctionStatus.OPEN; }
}
