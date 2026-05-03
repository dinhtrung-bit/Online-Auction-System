package server.dao.interfaces;

import server.dao.core.GenericDAO;
import server.models.auction.AuctionRoom;

import java.math.BigDecimal;
import java.util.List;

public interface AuctionRoomDAO extends GenericDAO<AuctionRoom> {
    // Lọc các phiên đấu giá theo trạng thái (OPEN, RUNNING, FINISHED, CANCELED)
    List<AuctionRoom> findByStatus(String status) throws Exception;

    // Hàm update chuyên dụng hỗ trợ Optimistic Lock
    void update(AuctionRoom room, BigDecimal oldPrice) throws Exception;
}