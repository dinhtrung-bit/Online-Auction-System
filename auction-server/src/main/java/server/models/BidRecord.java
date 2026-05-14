package server.models.auction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity lưu một lần đặt giá vào DB.
 * Thay thế BidMessage trong tầng DAO và Service.
 * Không implements Serializable vì không cần gửi qua mạng.
 */
public class BidRecord {

    private int auctionRoomId;
    private int bidderId;
    private BigDecimal bidAmount;
    private LocalDateTime timestamp;

    public BidRecord() {}

    public BidRecord(int auctionRoomId, int bidderId, BigDecimal bidAmount) {
        this.auctionRoomId = auctionRoomId;
        this.bidderId      = bidderId;
        this.bidAmount     = bidAmount;
        this.timestamp     = LocalDateTime.now();
    }

    public int getAuctionRoomId()       { return auctionRoomId; }
    public int getBidderId()            { return bidderId; }
    public BigDecimal getBidAmount()    { return bidAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setAuctionRoomId(int auctionRoomId)     { this.auctionRoomId = auctionRoomId; }
    public void setBidderId(int bidderId)               { this.bidderId = bidderId; }
    public void setBidAmount(BigDecimal bidAmount)      { this.bidAmount = bidAmount; }
    public void setTimestamp(LocalDateTime timestamp)   { this.timestamp = timestamp; }
}