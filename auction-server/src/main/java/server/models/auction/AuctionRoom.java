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
    private List<AutoBidConfig> inMemoryAutoBidders;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private int extensionCount = 0;

    private static final int MAX_EXTENSIONS = 5;
    private static final int ANTI_SNIPE_THRESHOLD_SECONDS = 30;
    private static final int ANTI_SNIPE_EXTENSION_SECONDS = 60;

    public AuctionRoom() {
        this.bidHistory = new ArrayList<>();
        this.inMemoryAutoBidders = new ArrayList<>();
        this.status = AuctionStatus.OPEN;
        this.currentPrice = BigDecimal.ZERO;
        this.startPrice = BigDecimal.ZERO;
    }

    public AuctionRoom(int id, int sellerID, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        if (item == null) {
            throw new IllegalArgumentException("AuctionRoom phải có item.");
        }

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian phiên đấu giá không hợp lệ.");
        }

        this.id = id;
        this.sellerID = sellerID;
        this.item = item;
        this.itemID = item.getItemId();
        this.startPrice = item.getStartingPrice() != null ? item.getStartingPrice() : BigDecimal.ZERO;
        this.currentPrice = this.startPrice;
        this.bidHistory = new ArrayList<>();
        this.inMemoryAutoBidders = new ArrayList<>();
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = LocalDateTime.now().isBefore(startTime)
                ? AuctionStatus.OPEN
                : AuctionStatus.RUNNING;
    }

    public boolean isExpired() {
        return endTime != null && LocalDateTime.now().isAfter(endTime);
    }

    public synchronized void placeBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        validateBidderAndAmount(bidder, amount);

        LocalDateTime serverNow = LocalDateTime.now();

        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            this.status = AuctionStatus.FINISHED;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            throw new InvalidBidException(
                    "Từ chối: Phiên đấu giá đã kết thúc! Server Time: " + serverNow.format(fmt)
            );
        }

        BigDecimal basePrice = currentPrice != null ? currentPrice : BigDecimal.ZERO;

        if (amount.compareTo(basePrice) <= 0) {
            throw new InvalidBidException("Từ chối: Giá đặt phải lớn hơn " + basePrice);
        }

        if (bidder.getAccountBalance() == null || bidder.getAccountBalance().compareTo(amount) < 0) {
            throw new InvalidBidException("Từ chối: Tài khoản không đủ số dư!");
        }

        applyNewWinner(bidder, amount, "Manual Bid");
    }

    public synchronized void registerAutoBid(Bidder bidder, BigDecimal maxBid, BigDecimal increment)
            throws InvalidBidException {

        validateBidderAndAmount(bidder, maxBid);

        if (increment == null || increment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBidException("Bước nhảy auto-bid phải lớn hơn 0.");
        }

        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            throw new InvalidBidException("Không thể thiết lập Auto-bid lúc này!");
        }

        if (bidder.getAccountBalance() == null || bidder.getAccountBalance().compareTo(maxBid) < 0) {
            throw new InvalidBidException("Số dư không đủ để bảo lãnh mức Max Bid này!");
        }

        ensureLists();

        AutoBidConfig config = new AutoBidConfig(this.id, bidder, maxBid, increment);
        inMemoryAutoBidders.add(config);

        System.out.println(">>> [Auto-Bid] " + bidder.getUsername()
                + " kích hoạt: Max=" + maxBid + ", Bước=" + increment);

        processInMemoryAutoBids();
    }

    private void processInMemoryAutoBids() {
        ensureLists();

        if (inMemoryAutoBidders.isEmpty()) return;

        inMemoryAutoBidders.sort(Comparator.comparing(AutoBidConfig::getRegisterTime));

        boolean newBidPlaced;

        do {
            newBidPlaced = false;

            for (AutoBidConfig config : inMemoryAutoBidders) {
                if (config == null || config.getBidder() == null) continue;

                if (currentWinner != null
                        && currentWinner.getUserId() == config.getBidder().getUserId()) {
                    continue;
                }

                BigDecimal increment = config.getIncrement() != null
                        ? config.getIncrement()
                        : BigDecimal.ONE;

                BigDecimal next = getSafeCurrentPrice().add(increment);

                if (next.compareTo(config.getMaxBid()) <= 0
                        && config.getBidder().getAccountBalance().compareTo(next) >= 0) {
                    applyNewWinner(config.getBidder(), next, "Auto-Bid Step");
                    newBidPlaced = true;
                    break;
                }

                boolean canWin = config.getMaxBid().compareTo(getSafeCurrentPrice()) > 0;
                boolean tieBreak = config.getMaxBid().compareTo(getSafeCurrentPrice()) == 0
                        && isOlderThanCurrentWinner(config);

                if ((canWin || tieBreak)
                        && config.getBidder().getAccountBalance().compareTo(config.getMaxBid()) >= 0) {
                    applyNewWinner(config.getBidder(), config.getMaxBid(), "Auto-Bid ALL-IN");
                    newBidPlaced = true;
                    break;
                }
            }

        } while (newBidPlaced);
    }

    private boolean isOlderThanCurrentWinner(AutoBidConfig challenger) {
        if (challenger == null || challenger.getRegisterTime() == null) return false;
        if (currentWinner == null) return true;

        ensureLists();

        for (AutoBidConfig config : inMemoryAutoBidders) {
            if (config == null || config.getBidder() == null) continue;

            if (config.getBidder().getUserId() == currentWinner.getUserId()) {
                return challenger.getRegisterTime().isBefore(config.getRegisterTime());
            }
        }

        return true;
    }

    public synchronized void placeAutoBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        validateBidderAndAmount(bidder, amount);

        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            throw new InvalidBidException("Phiên đã kết thúc, không thể auto-bid.");
        }

        if (amount.compareTo(getSafeCurrentPrice()) <= 0) {
            throw new InvalidBidException("Auto-bid thấp hơn giá hiện tại, bỏ qua.");
        }

        applyNewWinner(bidder, amount, "Auto-Bid");
    }

    private void applyNewWinner(Bidder bidder, BigDecimal amount, String logType) {
        ensureLists();

        this.currentPrice = amount;
        this.currentWinner = bidder;

        this.bidHistory.add(new BidMessage(this.id, bidder.getUserId(), amount));

        System.out.println("    [" + logType + "] "
                + bidder.getUsername() + " vươn lên với giá: " + amount);

        triggerAntiSniping();
    }

    private void triggerAntiSniping() {
        if (extensionCount >= MAX_EXTENSIONS) return;
        if (endTime == null) return;

        LocalDateTime now = LocalDateTime.now();

        if (!now.isAfter(endTime)
                && !endTime.isAfter(now.plusSeconds(ANTI_SNIPE_THRESHOLD_SECONDS))) {
            endTime = now.plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS);
            extensionCount++;

            System.out.println(">>> [Anti-sniping] Gia hạn lần " + extensionCount
                    + ": endTime mới = " + endTime);
        }
    }

    private void validateBidderAndAmount(Bidder bidder, BigDecimal amount) throws InvalidBidException {
        if (bidder == null) {
            throw new InvalidBidException("Bidder không hợp lệ.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBidException("Số tiền đặt giá phải lớn hơn 0.");
        }
    }

    private BigDecimal getSafeCurrentPrice() {
        return currentPrice != null ? currentPrice : BigDecimal.ZERO;
    }

    private void ensureLists() {
        if (bidHistory == null) {
            bidHistory = new ArrayList<>();
        }

        if (inMemoryAutoBidders == null) {
            inMemoryAutoBidders = new ArrayList<>();
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public int getSellerID() {
        return sellerID;
    }

    public void setSellerID(int sellerID) {
        this.sellerID = sellerID;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;

        if (item != null) {
            this.itemID = item.getItemId();

            if (this.startPrice == null) {
                this.startPrice = item.getStartingPrice();
            }

            if (this.currentPrice == null) {
                this.currentPrice = item.getStartingPrice();
            }
        }
    }

    public int getItemID() {
        return itemID;
    }

    public void setItemID(int itemID) {
        this.itemID = itemID;
    }

    public BigDecimal getStartPrice() {
        return startPrice != null ? startPrice : BigDecimal.ZERO;
    }

    public void setStartPrice(BigDecimal startPrice) {
        this.startPrice = startPrice != null ? startPrice : BigDecimal.ZERO;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice != null ? currentPrice : BigDecimal.ZERO;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice != null ? currentPrice : BigDecimal.ZERO;
    }

    public User getCurrentWinner() {
        return currentWinner;
    }

    public void setCurrentWinner(User currentWinner) {
        this.currentWinner = currentWinner;
    }

    public List<BidMessage> getBidHistory() {
        ensureLists();
        return bidHistory;
    }

    public void setBidHistory(List<BidMessage> bids) {
        this.bidHistory = bids != null ? bids : new ArrayList<>();
    }

    public LocalDateTime getStarttime() {
        return startTime;
    }

    public void setStarttime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public AuctionStatus getStatus() {
        return status != null ? status : AuctionStatus.OPEN;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status != null ? status : AuctionStatus.OPEN;
    }
}