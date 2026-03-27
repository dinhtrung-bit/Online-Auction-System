package src.main.java.com.auctions.client.controllers;

public interface IBidService {
    // Hàm xử lý đặt giá chính thức
    void processBid(int userId, int auctionId, double amount) throws Exception;

    // Hàm cho chức năng nâng cao Auto-bid
    void setupAutoBid(int userId, int auctionId, double maxBid, double increment);
}
