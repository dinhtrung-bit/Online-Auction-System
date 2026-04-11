package com.auctions.server.DAO;

import com.auctions.server.models.BidMessage;

import java.util.List;

public interface BidMessageDAO extends GenericDAO<BidMessage> {

    // Lấy toàn bộ lịch sử các lần đặt giá của một phòng đấu giá
    List<BidMessage> getBidHistoryByAuctionRoomId(int auctionRoomId) throws Exception;

    // Lấy ra mức giá đặt cao nhất hiện tại của phòng đấu giá
    BidMessage getHighestBid(int auctionRoomId) throws Exception;
}
