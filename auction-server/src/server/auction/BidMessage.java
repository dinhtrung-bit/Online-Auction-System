package server.auction;

import java.io.Serializable;
import java.time.LocalDateTime;

// Đối tượng vận chuyển lệnh đặt giá qua mạng
public class BidMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long bidderId;
    private Long auctionRoomId;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidMessage(Long bidderId, Long auctionRoomId, double bidAmount) {
        this.bidderId = bidderId;
        this.auctionRoomId = auctionRoomId;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public Long getBidderId() { return bidderId; }
    public Long getAuctionRoomId() { return auctionRoomId; }
    public double getBidAmount() { return bidAmount; }
}
