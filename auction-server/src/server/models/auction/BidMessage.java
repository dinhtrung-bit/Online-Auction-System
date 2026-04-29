package server.models.auction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Đây là đối tượng DTO (Data Transfer Object) kiêm Entity
// Dùng để đóng gói dữ liệu đặt giá và gửi qua mạng (Socket)
public class BidMessage implements Serializable {

    // Thuộc tính cực kỳ quan trọng để Client và Server hiểu cùng một phiên bản class
    private static final long serialVersionUID = 1L;

    private int bidderId;
    private int auctionRoomId;
    private BigDecimal bidAmount;
    private LocalDateTime timestamp;

    // Constructor đầy đủ tham số khi người dùng bấm nút "Đặt giá"
    public BidMessage(int bidderId, int auctionRoomId, BigDecimal bidAmount) {
        this.bidderId = bidderId;
        this.auctionRoomId = auctionRoomId;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now(); // Tự động ghi nhận khoảnh khắc đặt giá
    }

    // Constructor rỗng (Bắt buộc phải có nếu P1 và P3 dùng thư viện Gson/Jackson chuyển sang JSON)
    public BidMessage() {
    }

    // ================= GETTER VÀ SETTER =================

    public int getBidderId() {
        return bidderId;
    }

    public void setBidderId(int bidderId) {
        this.bidderId = bidderId;
    }

    public int getAuctionRoomId() {
        return auctionRoomId;
    }

    public void setAuctionRoomId(int auctionRoomId) {
        this.auctionRoomId = auctionRoomId;
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

    // Hàm toString giúp P1 in log ra màn hình console Server để dễ debug tìm lỗi
    @Override
    public String toString() {
        return "BidMessage{" +
                "bidderId=" + bidderId +
                ", roomId=" + auctionRoomId +
                ", amount=" + bidAmount +
                ", time=" + timestamp +
                '}';
    }
}