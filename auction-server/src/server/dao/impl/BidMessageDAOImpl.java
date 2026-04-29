package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.BidMessageDAO;
import server.models.auction.BidMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BidMessageDAOImpl implements BidMessageDAO {

    @Override
    public void insert(BidMessage obj) throws Exception {
        String sql = """
                INSERT INTO bid_message (auction_id, bidder_id, bid_amount, bid_time)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, obj.getAuctionRoomId());
            ps.setInt(2, obj.getBidderId());
            ps.setBigDecimal(3, obj.getBidAmount());
            ps.setTimestamp(4, Timestamp.valueOf(obj.getTimestamp()));

            ps.executeUpdate();
        }
    }

    @Override
    public void update(BidMessage obj) throws Exception {
        String sql = """
                UPDATE bid_message
                SET auction_id = ?, bidder_id = ?, bid_amount = ?, bid_time = ?
                WHERE auction_id = ? AND bidder_id = ? AND bid_time = ?
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, obj.getAuctionRoomId());
            ps.setInt(2, obj.getBidderId());
            ps.setBigDecimal(3, obj.getBidAmount());
            ps.setTimestamp(4, Timestamp.valueOf(obj.getTimestamp()));

            ps.setLong(5, obj.getAuctionRoomId());
            ps.setInt(6, obj.getBidderId());
            ps.setTimestamp(7, Timestamp.valueOf(obj.getTimestamp()));

            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM bid_message WHERE transaction_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<BidMessage> findAll() throws Exception {
        List<BidMessage> list = new ArrayList<>();

        String sql = """
                SELECT bidder_id, auction_id, bid_amount, bid_time
                FROM bid_message
                ORDER BY bid_time ASC
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToBidMessage(rs));
            }
        }

        return list;
    }

    @Override
    public List<BidMessage> getBidHistoryByAuctionRoomId(int auctionRoomId) throws Exception {
        List<BidMessage> list = new ArrayList<>();

        String sql = """
                SELECT bidder_id, auction_id, bid_amount, bid_time
                FROM bid_message
                WHERE auction_id = ?
                ORDER BY bid_time ASC
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auctionRoomId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToBidMessage(rs));
                }
            }
        }

        return list;
    }

    @Override
    public BidMessage getHighestBid(int auctionRoomId) throws Exception {
        String sql = """
                SELECT bidder_id, auction_id, bid_amount, bid_time
                FROM bid_message
                WHERE auction_id = ?
                ORDER BY bid_amount DESC, bid_time ASC
                LIMIT 1
                """;

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auctionRoomId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBidMessage(rs);
                }
            }
        }

        return null;
    }

    private BidMessage mapResultSetToBidMessage(ResultSet rs) throws SQLException {
        BidMessage bidMessage = new BidMessage();

        bidMessage.setBidderId(rs.getInt("bidder_id"));
        bidMessage.setAuctionRoomId(rs.getInt("auction_id"));
        bidMessage.setBidAmount(rs.getBigDecimal("bid_amount"));

        Timestamp timestamp = rs.getTimestamp("bid_time");
        if (timestamp != null) {
            bidMessage.setTimestamp(timestamp.toLocalDateTime());
        }

        return bidMessage;
    }
}
