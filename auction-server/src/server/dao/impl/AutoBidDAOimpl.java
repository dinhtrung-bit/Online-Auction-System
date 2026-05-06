package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.AutoBidDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AutoBidConfig;
import server.models.users.Bidder;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AutoBidDAOimpl implements AutoBidDAO {

    @Override
    public void insert(Object obj) throws Exception {
        AutoBidConfig autoBid = (AutoBidConfig) obj;

        String sql = """
                INSERT INTO auto_bids 
                (auction_id, bidder_id, max_bid, increment_step, created_at)
                VALUES (?, ?, ?, ?, NOW())
                """;

        Connection conn = DBConnection.getInstance().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, autoBid.getAuctionId().getId());
            pstmt.setInt(2, autoBid.getBidder().getUserId());
            pstmt.setBigDecimal(3, autoBid.getMaxBid());
            pstmt.setBigDecimal(4, autoBid.getIncrement());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    autoBid.setId(rs.getInt(1));
                }
            }
        }
    }

    @Override
    public void update(Object obj) throws Exception {
        AutoBidConfig autoBid = (AutoBidConfig) obj;

        String sql = """
                UPDATE auto_bids 
                SET auction_id = ?, bidder_id = ?, max_bid = ?, increment_step = ?
                WHERE autobid_id = ?
                """;

        Connection conn = DBConnection.getInstance().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, autoBid.getAuctionId().getId());
            pstmt.setInt(2, autoBid.getBidder().getUserId());
            pstmt.setBigDecimal(3, autoBid.getMaxBid());
            pstmt.setBigDecimal(4, autoBid.getIncrement());
            pstmt.setInt(5, autoBid.getId());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM auto_bids WHERE autobid_id = ?";

        Connection conn = DBConnection.getInstance().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public Object findById(int id) throws Exception {
        String sql = "SELECT * FROM auto_bids WHERE autobid_id = ?";

        Connection conn = DBConnection.getInstance().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAutoBid(rs);
                }
            }
        }

        return null;
    }

    @Override
    public List findAll() throws Exception {
        List<AutoBidConfig> list = new ArrayList<>();

        String sql = "SELECT * FROM auto_bids";

        Connection conn = DBConnection.getInstance().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToAutoBid(rs));
            }
        }

        return list;
    }

    @Override
    public List<AutoBidConfig> getAutoBidsByAuctionId(int auctionId) {
        List<AutoBidConfig> list = new ArrayList<>();

        String sql = """
                SELECT * FROM auto_bids 
                WHERE auction_id = ? 
                ORDER BY created_at ASC
                """;

        try {
            Connection conn = DBConnection.getInstance().getConnection();

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, auctionId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSetToAutoBid(rs));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    @Override
    public AutoBidConfig findByUserIdAndAuctionId(int userId, int auctionId) throws Exception {
        String sql = """
                SELECT * FROM auto_bids 
                WHERE bidder_id = ? AND auction_id = ?
                """;

        Connection conn = DBConnection.getInstance().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, auctionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAutoBid(rs);
                }
            }
        }

        return null;
    }

    @Override
    public void deleteByAuctionId(int auctionId) throws Exception {
        String sql = "DELETE FROM auto_bids WHERE auction_id = ?";

        Connection conn = DBConnection.getInstance().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            pstmt.executeUpdate();
        }
    }

    private AutoBidConfig mapResultSetToAutoBid(ResultSet rs) throws SQLException {
        AutoBidConfig autoBid = new AutoBidConfig();

        autoBid.setId(rs.getInt("autobid_id"));

        AuctionRoom auction = new AuctionRoom();
        auction.setId(rs.getInt("auction_id"));
        autoBid.setAuctionId(auction);

        Bidder bidder = new Bidder();
        bidder.setUserId(rs.getInt("bidder_id"));
        autoBid.setBidder(bidder);

        autoBid.setMaxBid(rs.getBigDecimal("max_bid"));
        autoBid.setIncrement(rs.getBigDecimal("increment_step"));

        Timestamp timestamp = rs.getTimestamp("created_at");
        if (timestamp != null) {
            autoBid.setRegisterTime(timestamp.toLocalDateTime());
        }

        return autoBid;
    }
}