package server.dao.impl;

import server.dao.interfaces.AuctionRoomDAO;
import server.dao.core.DBConnection;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Item;
import server.models.users.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class AuctionRoomDAOImpl implements AuctionRoomDAO {

    // DTO nội bộ để lưu dữ liệu thô từ ResultSet trước khi đóng
    private static class AuctionRoomRaw {
        int id, itemId, sellerId;
        BigDecimal currentPrice;
        Timestamp startTimeTS, endTimeTs;
        String statusStr;
        Integer winnerId;
    }

    @Override
    public void insert(AuctionRoom room) throws Exception {
        String sql = "INSERT INTO auctions (item_id, start_price, current_highest_price, start_time, end_time, status, winner_id, seller_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, room.getItem().getItemId());
            pstmt.setBigDecimal(2, room.getStartPrice());
            pstmt.setBigDecimal(3, room.getCurrentPrice());
            pstmt.setTimestamp(4, Timestamp.valueOf(room.getStarttime()));
            pstmt.setTimestamp(5, Timestamp.valueOf(room.getEndTime()));
            pstmt.setString(6, room.getStatus().name());

            if (room.getCurrentWinner() != null) {
                pstmt.setInt(7, room.getCurrentWinner().getUserId());
            } else {
                pstmt.setNull(7, java.sql.Types.INTEGER);
            }

            pstmt.setInt(8, room.getSellerID());
            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(AuctionRoom room) throws Exception {
        this.update(room, room.getCurrentPrice());
    }

    @Override
    public void update(AuctionRoom room, BigDecimal oldPrice) throws Exception {
        String sql;
        if (oldPrice == null) {
            sql = "UPDATE auctions SET current_highest_price = ?, winner_id = ?, end_time = ?, status = ? " +
                    "WHERE auction_id = ? AND current_highest_price IS NULL";
        } else {
            sql = "UPDATE auctions SET current_highest_price = ?, winner_id = ?, end_time = ?, status = ? " +
                    "WHERE auction_id = ? AND current_highest_price = ?";
        }

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBigDecimal(1, room.getCurrentPrice());

            if (room.getCurrentWinner() != null) {
                pstmt.setInt(2, room.getCurrentWinner().getUserId());
            } else {
                pstmt.setNull(2, java.sql.Types.INTEGER);
            }
            pstmt.setTimestamp(3, java.sql.Timestamp.valueOf(room.getEndTime()));
            pstmt.setString(4, room.getStatus().name());
            pstmt.setInt(5, room.getId());

            if (oldPrice != null) {
                pstmt.setBigDecimal(6, oldPrice);
            }

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new Exception("Xung đột dữ liệu (Lost Update): Đã có người khác đặt giá cao hơn trong tích tắc!");
            }
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM auctions WHERE auction_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<AuctionRoom> findAll() throws Exception {
        List<AuctionRoomRaw> rawList = new ArrayList<>();
        String sql = "SELECT * FROM auctions";

        // BƯỚC 1: Đọc hết dữ liệu thô từ ResultSet rồi đóng lại
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                rawList.add(readRaw(rs));
            }
        } // ResultSet đã đóng ở đây

        // BƯỚC 2: Sau khi RS đã đóng, mới gọi DAO khác để fetch item/user
        List<AuctionRoom> rooms = new ArrayList<>();
        for (AuctionRoomRaw raw : rawList) {
            rooms.add(buildFromRaw(raw));
        }
        return rooms;
    }

    @Override
    public List<AuctionRoom> findByStatus(String status) throws Exception {
        List<AuctionRoomRaw> rawList = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ?";

        // BƯỚC 1: Đọc hết dữ liệu thô
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status.toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rawList.add(readRaw(rs));
                }
            }
        } // ResultSet đã đóng ở đây

        // BƯỚC 2: Fetch item/user sau khi RS đóng
        List<AuctionRoom> rooms = new ArrayList<>();
        for (AuctionRoomRaw raw : rawList) {
            rooms.add(buildFromRaw(raw));
        }
        return rooms;
    }

    @Override
    public AuctionRoom findById(int id) throws Exception {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";
        AuctionRoomRaw raw = null;

        // BƯỚC 1: Đọc dữ liệu thô rồi đóng RS
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    raw = readRaw(rs);
                }
            }
        } // ResultSet đã đóng ở đây

        // BƯỚC 2: Fetch item/user nếu có kết quả
        return (raw != null) ? buildFromRaw(raw) : null;
    }

    /**
     * Đọc dữ liệu nguyên thủy từ ResultSet vào một object trung gian.
     * KHÔNG gọi bất kỳ DAO nào bên trong phương thức này.
     */
    private AuctionRoomRaw readRaw(ResultSet rs) throws Exception {
        AuctionRoomRaw raw = new AuctionRoomRaw();
        raw.id = rs.getInt("auction_id");
        raw.itemId = rs.getInt("item_id");
        raw.sellerId = rs.getInt("seller_id");
        raw.currentPrice = rs.getBigDecimal("current_highest_price");
        raw.startTimeTS = rs.getTimestamp("start_time");
        raw.endTimeTs = rs.getTimestamp("end_time");
        raw.statusStr = rs.getString("status");
        raw.winnerId = (Integer) rs.getObject("winner_id");
        return raw;
    }

    /**
     * Dựng AuctionRoom từ dữ liệu thô. Gọi các DAO khác ở đây là an toàn
     * vì ResultSet gốc đã được đóng trước khi phương thức này được gọi.
     */
    private AuctionRoom buildFromRaw(AuctionRoomRaw raw) throws Exception {
        ItemDAO itemDAO = new ItemDAOImpl();
        UserDAO userDAO = new UserDAOImpl();

        Item item = itemDAO.findById(raw.itemId);

        User winner = null;
        if (raw.winnerId != null) {
            winner = userDAO.findById(raw.winnerId);
        }

        AuctionRoom room = new AuctionRoom(
                raw.id,
                raw.sellerId,
                item,
                raw.startTimeTS.toLocalDateTime(),
                raw.endTimeTs.toLocalDateTime()
        );

        room.setStatus(AuctionStatus.valueOf(raw.statusStr));

        if (raw.currentPrice != null) {
            room.setCurrentPrice(raw.currentPrice);
        }
        if (winner != null) {
            room.setCurrentWinner(winner);
        }

        return room;
    }
}