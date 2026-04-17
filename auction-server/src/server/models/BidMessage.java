<<<<<<< HEAD
<<<<<<< HEAD
package src.server.models;
=======
package server.models;
>>>>>>> dinhtrung
=======
package src.server.models;
>>>>>>> thắng

import java.io.Serializable;
import java.time.LocalDateTime;

// Đây là đối tượng DTO (Data Transfer Object) kiêm Entity
// Dùng để đóng gói dữ liệu đặt giá và gửi qua mạng (Socket)
public class BidMessage implements Serializable {

    // Thuộc tính cực kỳ quan trọng để Client và Server hiểu cùng một phiên bản class
    private static final long serialVersionUID = 1L;

    private Long bidderId;
    private Long auctionRoomId;
    private double bidAmount;
    private LocalDateTime timestamp;

    // Constructor đầy đủ tham số khi người dùng bấm nút "Đặt giá"
    public BidMessage(Long bidderId, Long auctionRoomId, double bidAmount) {
        this.bidderId = bidderId;
        this.auctionRoomId = auctionRoomId;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now(); // Tự động ghi nhận khoảnh khắc đặt giá
    }

    // Constructor rỗng (Bắt buộc phải có nếu P1 và P3 dùng thư viện Gson/Jackson chuyển sang JSON)
    public BidMessage() {
    }

    // ================= GETTER VÀ SETTER =================

    public Long getBidderId() {
        return bidderId;
    }

    public void setBidderId(Long bidderId) {
        this.bidderId = bidderId;
    }

    public Long getAuctionRoomId() {
        return auctionRoomId;
    }

    public void setAuctionRoomId(Long auctionRoomId) {
        this.auctionRoomId = auctionRoomId;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(double bidAmount) {
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