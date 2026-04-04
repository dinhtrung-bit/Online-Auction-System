package src.main.java.com.auctions.server.DAO;


import src.main.java.com.auctions.server.models.AuctionRoom;

import java.util.List;

public interface AuctionRoomDAO extends GenericDAO<AuctionRoom> {
    // Lọc các phiên đấu giá theo trạng thái (OPEN, RUNNING, FINISHED)
    List<AuctionRoom> findByStatus(String status) throws Exception;
}