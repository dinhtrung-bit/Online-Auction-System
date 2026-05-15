package server.networks;

import server.networks.ClientHandler;
import server.networks.interfaces.BroadcastChannel;

import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionBroadcastManager implements BroadcastChannel {
    private final CopyOnWriteArrayList<ClientHandler> clients;

    public AuctionBroadcastManager(CopyOnWriteArrayList<ClientHandler> clients) {
        this.clients = clients;
    }

    @Override
    public void broadcast(String json) {
        clients.forEach(c -> c.sendMessage(json));
    }
}


