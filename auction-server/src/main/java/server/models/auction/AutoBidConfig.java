package server.models.auction;

import server.models.users.Bidder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AutoBidConfig implements Comparable<AutoBidConfig> {

    private int id;
    private int auctionId;
    private Bidder bidder;
    private BigDecimal maxBid;
    private BigDecimal increment;
    private LocalDateTime registerTime;

    public AutoBidConfig() {
        this.registerTime = LocalDateTime.now();
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
        this.registerTime = registerTime != null ? registerTime : LocalDateTime.now();
    }

    @Override
    public int compareTo(AutoBidConfig other) {
        if (other == null) return -1;

        LocalDateTime thisTime = this.registerTime != null ? this.registerTime : LocalDateTime.now();
        LocalDateTime otherTime = other.registerTime != null ? other.registerTime : LocalDateTime.now();

        return thisTime.compareTo(otherTime);
    }
}