package server.dao.interfaces;

import server.dao.core.GenericDAO;
import server.models.auction.AutoBidConfig;

import java.util.List;

public interface AutoBidDAO extends GenericDAO<AutoBidConfig> {

    List<AutoBidConfig> getAutoBidsByAuctionId(int auctionId) throws Exception;

    AutoBidConfig findByUserIdAndAuctionId(int userId, int auctionId) throws Exception;

    void deleteByAuctionIdAndBidderId(int auctionId, int bidderId) throws Exception;
}