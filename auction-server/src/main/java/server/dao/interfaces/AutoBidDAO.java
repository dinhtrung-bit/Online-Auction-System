package server.dao.interfaces;

import server.dao.core.GenericDAO;
import server.models.auction.AutoBidConfig;

import java.util.List;

public interface AutoBidDAO extends GenericDAO {
    List<AutoBidConfig> getAutoBidsByAuctionId(int auctionId);
    AutoBidConfig findByUserIdAndAuctionId(int userId, int auctionId) throws Exception;
    void deleteByAuctionId(int auctionId) throws Exception;
}
