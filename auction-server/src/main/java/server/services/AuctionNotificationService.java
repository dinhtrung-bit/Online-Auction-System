package server.services;

import com.google.gson.Gson;

import server.networks.AuctionBroadcastManager;
import server.networks.dto.MessageDTO;
import server.networks.interfaces.BroadcastChannel;

/**
 * Chỉ phụ trách gửi thông báo realtime cho client.
 */
public class AuctionNotificationService {

    private static final Gson GSON = new Gson();

    private final BroadcastChannel broadcaster;
    private final AuctionBroadcastManager roomBroadcaster;

    public AuctionNotificationService(AuctionBroadcastManager broadcaster) {
        this.broadcaster = broadcaster;
        this.roomBroadcaster = broadcaster;
    }

    /** Broadcast tới TẤT CẢ client (cho sự kiện toàn hệ thống). */
    public void broadcast(String action, String payload) {
        broadcaster.broadcast(GSON.toJson(new MessageDTO(action, payload)));
    }

    /**
     * Broadcast chỉ tới các client đang xem phòng đấu giá có ID = roomId.
     * Dùng cho: UPDATE_PRICE, AUCTION_FINISHED, AUCTION_CANCELED.
     */
    public void broadcastToRoom(long roomId, String action, String payload) {
        if (roomBroadcaster != null) {
            roomBroadcaster.broadcastToRoom(roomId, GSON.toJson(new MessageDTO(action, payload)));
        } else {
            broadcast(action, payload);
        }
    }

    /**
     * Gửi thông báo chỉ tới một user cụ thể đang ở trong phòng roomId.
     * Dùng cho AUTO_BID_EXCEEDED — chỉ người có AutoBid vượt giới hạn mới nhận,
     * không phải tất cả người trong phòng.
     */
    public void sendToUserInRoom(long roomId, int userId, String action, String payload) {
        if (roomBroadcaster != null) {
            roomBroadcaster.sendToUserInRoom(roomId, userId, GSON.toJson(new MessageDTO(action, payload)));
        } else {
            // fallback: không lý tưởng nhưng không crash
            broadcastToRoom(roomId, action, payload);
        }
    }
}