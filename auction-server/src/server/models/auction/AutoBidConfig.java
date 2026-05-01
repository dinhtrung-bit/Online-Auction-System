package server.models.auction;

import server.models.users.Bidder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AutoBidConfig implements Comparable<AutoBidConfig> {
    private int id;
    private AuctionRoom auctionId;
    private Bidder bidder;
    private BigDecimal maxBid;
    private BigDecimal increment;
    private LocalDateTime registerTime;

    public AutoBidConfig() {
    }

    public AutoBidConfig(AuctionRoom auctionId, Bidder bidder, BigDecimal maxBid, BigDecimal increment) {
        this.auctionId = auctionId;
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registerTime = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {   // chỗ này bạn đang thiếu tham số
        this.id = id;
    }

    public AuctionRoom getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(AuctionRoom auctionId) {
        this.auctionId = auctionId;
    }

    public Bidder getBidder() {
        return bidder;
    }

    public void setBidder(Bidder bidder) {   // chỗ này bạn cũng đang thiếu tham số
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