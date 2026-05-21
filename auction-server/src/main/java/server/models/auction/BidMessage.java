package server.models.auction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO dùng trong bidHistory của AuctionRoom (in-memory, truyền qua socket).
 * Chỉ dùng để theo dõi lịch sử bid trong RAM — KHÔNG lưu DB trực tiếp.
 * Tầng DAO và Service dùng BidRecord thay thế.
 */
public class BidMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private int auctionRoomId;
    private int bidderId;
    private BigDecimal bidAmount;
    private LocalDateTime timestamp;
    // contructor rỗng , dùng khi tần tạo object trước rồi set dữ liệu sau
    public BidMessage() {}

    // Constructor dùng trong AuctionRoom.applyNewWinner()
    public BidMessage(int auctionRoomId, int bidderId, BigDecimal bidAmount) {
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