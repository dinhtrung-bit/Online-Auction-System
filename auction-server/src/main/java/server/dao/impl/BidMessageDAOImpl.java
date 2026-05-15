package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.BidMessageDAO;
import server.models.auction.BidRecord;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BidMessageDAOImpl implements BidMessageDAO {

    @Override
    public void insert(BidRecord obj) throws Exception {
        if (obj == null) {
            throw new IllegalArgumentException("BidRecord không được null.");
        }

        String sql = """
                INSERT INTO bid_message (auction_id, bidder_id, bid_amount, bid_time)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, obj.getAuctionRoomId());
            ps.setInt(2, obj.getBidderId());
            ps.setBigDecimal(3, obj.getBidAmount());

            LocalDateTime time = obj.getTimestamp() != null
                    ? obj.getTimestamp()
                    : LocalDateTime.now();

            ps.setTimestamp(4, Timestamp.valueOf(time));

            ps.executeUpdate();
        }
    }

    @Override
    public void update(BidRecord obj) throws Exception {
        if (obj == null) {
            throw new IllegalArgumentException("BidRecord không được null.");
        }

        String sql = """
                UPDATE bid_message
                SET bid_amount = ?
                WHERE auction_id = ? AND bidder_id = ? AND bid_time = ?
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, obj.getBidAmount());
            ps.setInt(2, obj.getAuctionRoomId());
            ps.setInt(3, obj.getBidderId());

            LocalDateTime time = obj.getTimestamp() != null
                    ? obj.getTimestamp()
                    : LocalDateTime.now();

            ps.setTimestamp(4, Timestamp.valueOf(time));

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy bid để cập nhật.");
            }
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM bid_message WHERE transaction_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy bid để xóa.");
            }
        }
    }

    @Override
    public List<BidRecord> findAll() throws Exception {
        List<BidRecord> list = new ArrayList<>();

        String sql = """
                SELECT transaction_id, bidder_id, auction_id, bid_amount, bid_time
                FROM bid_message
                ORDER BY bid_time ASC
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }

        return list;
    }

    @Override
    public BidRecord findById(int id) throws Exception {
        String sql = """
                SELECT transaction_id, bidder_id, auction_id, bid_amount, bid_time
                FROM bid_message
                WHERE transaction_id = ?
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }

        return null;
    }

    @Override
    public List<BidRecord> getBidHistoryByAuctionRoomId(int auctionRoomId) throws Exception {
        List<BidRecord> list = new ArrayList<>();

        String sql = """
                SELECT transaction_id, bidder_id, auction_id, bid_amount, bid_time
                FROM bid_message
                WHERE auction_id = ?
                ORDER BY bid_time ASC
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auctionRoomId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }

        return list;
    }

    @Override
    public BidRecord getHighestBid(int auctionRoomId) throws Exception {
        String sql = """
                SELECT transaction_id, bidder_id, auction_id, bid_amount, bid_time
                FROM bid_message
                WHERE auction_id = ?
                ORDER BY bid_amount DESC, bid_time ASC
                LIMIT 1
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auctionRoomId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }

        return null;
    }

    private BidRecord mapRow(ResultSet rs) throws SQLException {
        BidRecord record = new BidRecord();

        record.setBidderId(rs.getInt("bidder_id"));
        record.setAuctionRoomId(rs.getInt("auction_id"));
        record.setBidAmount(rs.getBigDecimal("bid_amount"));

        Timestamp timestamp = rs.getTimestamp("bid_time");
        if (timestamp != null) {
            record.setTimestamp(timestamp.toLocalDateTime());
        }

        return record;
    }
}