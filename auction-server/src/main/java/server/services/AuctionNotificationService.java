package server.services;

import com.google.gson.Gson;

import server.networks.dto.MessageDTO;
import server.networks.interfaces.BroadcastChannel;

/**
 * Chỉ phụ trách gửi thông báo realtime cho client.
 * Không thay đổi action/payload so với code cũ trong AuctionService.
 */
public class AuctionNotificationService {

    private static final Gson GSON = new Gson();

    private final BroadcastChannel broadcaster;

    public AuctionNotificationService(BroadcastChannel broadcaster) {
        this.broadcaster = broadcaster;
    }

    public void broadcast(String action, String payload) {
        broadcaster.broadcast(GSON.toJson(new MessageDTO(action, payload)));
    }
}
