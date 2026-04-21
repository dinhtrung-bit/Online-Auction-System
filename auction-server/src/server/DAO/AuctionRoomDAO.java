package server.DAO;


import server.models.AuctionRoom;

import java.util.List;

public interface AuctionRoomDAO extends GenericDAO<AuctionRoom> {
    // Lọc các phiên đấu giá theo trạng thái (OPEN, RUNNING, FINISHED,CANCELED)
    List<AuctionRoom> findByStatus(String status) throws Exception;
}