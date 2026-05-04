package server.models.auction;

public enum AuctionStatus {
    OPEN,       // Sắp diễn ra
    RUNNING,    // Đang đấu giá
    FINISHED,   // Đã kết thúc thời gian (chờ thanh toán)
    PAID,       // Đã thanh toán thành công
    CANCELED    // Bị hủy (do không có người bid hoặc thanh toán lỗi)
}