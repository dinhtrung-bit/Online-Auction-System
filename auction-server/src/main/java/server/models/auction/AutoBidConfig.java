package server.models.auction;

import server.models.users.Bidder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AutoBidConfig — cấu hình đặt giá tự động của một Bidder cho một phiên đấu giá.
 *
 * Fix 3.1 (naming + type):
 *   Trường auctionId giờ là int thay vì AuctionRoom.
 *   Tên "auctionId" khớp với kiểu int — không còn nhập nhằng "tên là id nhưng kiểu là Object".
 *   AuctionService.registerAutoBid() không cần tạo AuctionRoom rỗng làm workaround nữa.
 *   AutoBidDAOImpl đọc trực tiếp getAuctionId() (int) thay vì getAuctionId().getId().
 */
public class AutoBidConfig implements Comparable<AutoBidConfig> {

    private int id;
    private int auctionId;   // int — khớp tên và kiểu
    private Bidder bidder;
    private BigDecimal maxBid;
    private BigDecimal increment;
    private LocalDateTime registerTime;

    public AutoBidConfig() {
    }

    public AutoBidConfig(int auctionId, Bidder bidder, BigDecimal maxBid, BigDecimal increment) {
        this.auctionId = auctionId;
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registerTime = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(int auctionId) {
        this.auctionId = auctionId;
    }

    public Bidder getBidder() {
        return bidder;
    }

    public void setBidder(Bidder bidder) {
        this.bidder = bidder;
    }

    public BigDecimal getMaxBid() {
        return maxBid;
    }

    public void setMaxBid(BigDecimal maxBid) {
        this.maxBid = maxBid;
    }

    public BigDecimal getIncrement() {
        return increment;
    }

    public void setIncrement(BigDecimal increment) {
        this.increment = increment;
    }

    public LocalDateTime getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(LocalDateTime registerTime) {
        this.registerTime = registerTime;
    }

    @Override
    public int compareTo(AutoBidConfig other) {
        return this.registerTime.compareTo(other.registerTime);
    }
}