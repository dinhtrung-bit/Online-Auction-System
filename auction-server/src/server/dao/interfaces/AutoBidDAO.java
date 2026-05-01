package server.dao.interfaces;

import server.dao.core.GenericDAO;
import server.models.auction.AutoBidConfig;

import java.util.List;

public interface AutoBidDAO extends GenericDAO {
    boolean insertAutoBid(int auctionId, AutoBidConfig config);
    List<AutoBidConfig> getAutoBidsByAuctionId(int auctionId);

}
