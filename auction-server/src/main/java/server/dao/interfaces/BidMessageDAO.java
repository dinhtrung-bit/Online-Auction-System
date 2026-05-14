package server.dao.interfaces;

import server.models.auction.BidRecord;

import java.util.List;

/**
 * Interface DAO cho bảng bid_message.
 * Tất cả method dùng BidRecord (entity DB), không dùng BidMessage (DTO mạng).
 */
public interface BidMessageDAO {

    void insert(BidRecord obj) throws Exception;

    void update(BidRecord obj) throws Exception;

    void delete(int id) throws Exception;

    List<BidRecord> findAll() throws Exception;

    BidRecord findById(int id) throws Exception;

    List<BidRecord> getBidHistoryByAuctionRoomId(int auctionRoomId) throws Exception;

    BidRecord getHighestBid(int auctionRoomId) throws Exception;
}