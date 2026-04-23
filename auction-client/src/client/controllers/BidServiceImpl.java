package client.controllers;

import client.models.BidMessage;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class BidServiceImpl implements IBidService {

    private Socket socket;
    private ObjectOutputStream out;

    // Khởi tạo kết nối mạng
    public BidServiceImpl(Socket socket, ObjectOutputStream out) {
        this.socket = socket;
        this.out = out;
    }

    @Override
    public void processBid(int userId, int auctionId, double amount) throws Exception {
        // 1. Đóng gói dữ liệu người dùng nhập thành DTO
        BidMessage newBid = new BidMessage((long) userId, (long) auctionId, amount);

        // 2. Gửi đối tượng này qua Server
        out.writeObject(newBid);
        out.flush();
        System.out.println("Đã gửi yêu cầu đặt giá " + amount + " lên Server.");
    }

    @Override
    public void setupAutoBid(int userId, int auctionId, double maxBid, double increment) {
        // Logic nâng cao cho chức năng Auto-bid sẽ viết ở đây
    }
}