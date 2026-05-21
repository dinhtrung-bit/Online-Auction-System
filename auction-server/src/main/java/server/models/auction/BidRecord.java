package server.models.auction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidRecord {

    private int auctionRoomId;
    private int bidderId;
    private BigDecimal bidAmount;
    private LocalDateTime timestamp;

    public BidRecord() {
        this.timestamp = LocalDateTime.now();
    }

    public BidRecord(int auctionRoomId, int bidderId, BigDecimal bidAmount) {
        this.auctionRoomId = auctionRoomId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public int getAuctionRoomId() {
        return auctionRoomId;
    }

    public void setAuctionRoomId(int auctionRoomId) {
        this.auctionRoomId = auctionRoomId;
    }

    public int getBidderId() {
        return bidderId;
    }

    public void setBidderId(int bidderId) {
        this.bidderId = bidderId;
    }

    public BigDecimal getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(BigDecimal bidAmount) {
        this.bidAmount = bidAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}