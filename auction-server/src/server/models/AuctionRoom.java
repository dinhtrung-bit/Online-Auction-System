<<<<<<< HEAD
package server.models;
=======
package src.server.models;
>>>>>>> e6fdc0c29820aeb4b000a79a18715e91b6bec51e
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// implements Serializable để chuẩn bị cho việc gửi dữ liệu qua Socket
public class AuctionRoom implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Item item;
    private double currentPrice;
    private User currentWinner;
    private List<BidMessage> bidHistory;

    private LocalDateTime endTime;
    private AuctionStatus status;

    public AuctionRoom(Long id, Item item, LocalDateTime endTime) {
        this.id = id;
        this.item = item;
        this.currentPrice = item.getStartingPrice();
        this.bidHistory = new ArrayList<>();
        this.endTime = endTime;
        this.status = AuctionStatus.RUNNING; // Mặc định khi tạo là đang chạy
    }

    // Kiểm tra xem thời gian phiên đấu giá đã kết thúc chưa
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endTime);
    }

    // Nghiệp vụ đặt giá: Đã thêm 'synchronized' để xử lý an toàn khi nhiều người đặt cùng lúc
    public synchronized void placeBid(Bidder bidder, double amount) throws Exception {

        // 1. Kiểm tra trạng thái và thời gian
        if (this.status != AuctionStatus.RUNNING || isExpired()) {
            this.status = AuctionStatus.FINISHED;
            throw new Exception("Lỗi: Phiên đấu giá này đã kết thúc hoặc chưa mở!");
        }

        // 2. Kiểm tra giá đặt (phải lớn hơn giá hiện tại)
        if (amount <= this.currentPrice) {
            throw new Exception("Lỗi: Giá đặt (" + amount + ") phải cao hơn giá hiện tại (" + this.currentPrice + ")!");
        }

        // 3. Kiểm tra số dư tài khoản của người dùng
        if (bidder.getBalance() < amount) {
            throw new Exception("Lỗi: Số dư tài khoản của bạn không đủ để đặt mức giá này!");
        }

        // 4. Cập nhật dữ liệu người dẫn đầu
        this.currentPrice = amount;
        this.currentWinner = bidder;

        // 5. Lưu vào lịch sử đấu giá
        // Chuyển userId từ String (trong User.java) sang Long (trong BidMessage.java)
        Long bidderIdLong = Long.parseLong(bidder.getUserId());
        BidMessage bidEntry = new BidMessage(bidderIdLong, this.id, amount);
        this.bidHistory.add(bidEntry);

        System.out.println(">>> [Cập nhật] " + bidder.getUsername() + " đã vươn lên dẫn đầu với mức giá: " + amount);

        // 6. Logic nâng cao lấy điểm cộng: Gia hạn phiên đấu giá (Anti-sniping Algorithm)
        // Nếu có người đặt giá trong 30 giây cuối, tự động cộng thêm 60 giây
        long secondsLeft = Duration.between(LocalDateTime.now(), this.endTime).getSeconds();
        if (secondsLeft > 0 && secondsLeft <= 30) {
            this.endTime = this.endTime.plusSeconds(60);
            System.out.println(">>> [Anti-sniping] Có người đặt giá phút chót! Phiên đấu giá được gia hạn thêm 60 giây.");
        }
    }

    // ================= GETTER VÀ SETTER =================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public User getCurrentWinner() { return currentWinner; }
    public void setCurrentWinner(User currentWinner) { this.currentWinner = currentWinner; }

    public List<BidMessage> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidMessage> bidHistory) { this.bidHistory = bidHistory; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }
}