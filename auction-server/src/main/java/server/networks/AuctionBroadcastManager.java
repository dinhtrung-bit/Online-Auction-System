package server.networks;

import server.networks.interfaces.BroadcastChannel;

import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionBroadcastManager implements BroadcastChannel {
    private final CopyOnWriteArrayList<ClientHandler> clients;

    public AuctionBroadcastManager(CopyOnWriteArrayList<ClientHandler> clients) {
        this.clients = clients;
    }

    /** Broadcast tới TẤT CẢ client (dùng cho các sự kiện toàn hệ thống). */
    @Override
    public void broadcast(String json) {
        clients.forEach(c -> c.sendMessage(json));
    }

    /**
     * Broadcast chỉ tới các client đang xem phòng đấu giá có ID = roomId.
     * Dùng cho: UPDATE_PRICE, AUCTION_FINISHED, AUCTION_CANCELED.
     */
    public void broadcastToRoom(long roomId, String json) {
        clients.stream()
                .filter(c -> c.isInRoom(roomId))
                .forEach(c -> c.sendMessage(json));
    }

    /**
     * Gửi message chỉ tới một user cụ thể (theo userId) đang ở trong phòng roomId.
     * Dùng cho AUTO_BID_EXCEEDED — chỉ người có AutoBid vượt giới hạn mới nhận.
     */
    public void sendToUserInRoom(long roomId, int userId, String json) {
        clients.stream()
                .filter(c -> c.isInRoom(roomId) && c.isLoggedInUser(userId))
                .forEach(c -> c.sendMessage(json));
    }
}