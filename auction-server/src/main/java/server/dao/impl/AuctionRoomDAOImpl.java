package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.ItemDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Item;
import server.models.users.User;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionRoomDAOImpl implements AuctionRoomDAO {

    private final ItemDAO itemDAO;
    private final UserDAO userDAO;

    public AuctionRoomDAOImpl(ItemDAO itemDAO, UserDAO userDAO) {
        this.itemDAO = itemDAO;
        this.userDAO = userDAO;
    }

    private static class AuctionRoomRaw {
        int id;
        int itemId;
        int sellerId;
        BigDecimal currentPrice;
        Timestamp startTimeTS;
        Timestamp endTimeTS;
        String statusStr;
        Integer winnerId;
    }

    @Override
    public void insert(AuctionRoom room) throws Exception {
        validateRoom(room);

        String sql = """
                INSERT INTO auctions
                (item_id, start_price, current_highest_price, start_time, end_time, status, winner_id, seller_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, room.getItem().getItemId());
            pstmt.setBigDecimal(2, room.getStartPrice());
            pstmt.setBigDecimal(3, room.getCurrentPrice());
            pstmt.setTimestamp(4, Timestamp.valueOf(room.getStarttime()));
            pstmt.setTimestamp(5, Timestamp.valueOf(room.getEndTime()));
            pstmt.setString(6, safeStatus(room).name());

            if (room.getCurrentWinner() != null) {
                pstmt.setInt(7, room.getCurrentWinner().getUserId());
            } else {
                pstmt.setNull(7, Types.INTEGER);
            }

            pstmt.setInt(8, room.getSellerID());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(AuctionRoom room) throws Exception {
        validateRoom(room);

        String sql = """
                UPDATE auctions
                SET current_highest_price = ?,
                    winner_id = ?,
                    end_time = ?,
                    status = ?
                WHERE auction_id = ?
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBigDecimal(1, room.getCurrentPrice());

            if (room.getCurrentWinner() != null) {
                pstmt.setInt(2, room.getCurrentWinner().getUserId());
            } else {
                pstmt.setNull(2, Types.INTEGER);
            }

            pstmt.setTimestamp(3, Timestamp.valueOf(room.getEndTime()));
            pstmt.setString(4, safeStatus(room).name());
            pstmt.setInt(5, room.getId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy phiên đấu giá để cập nhật.");
            }
        }
    }

    @Override
    public void updateWithOptimisticLock(AuctionRoom room, BigDecimal oldPrice) throws Exception {
        validateRoom(room);

        String sql;

        if (oldPrice == null) {
            sql = """
                    UPDATE auctions
                    SET current_highest_price = ?,
                        winner_id = ?,
                        end_time = ?,
                        status = ?
                    WHERE auction_id = ? AND current_highest_price IS NULL
                    """;
        } else {
            sql = """
                    UPDATE auctions
                    SET current_highest_price = ?,
                        winner_id = ?,
                        end_time = ?,
                        status = ?
                    WHERE auction_id = ? AND current_highest_price = ?
                    """;
        }

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBigDecimal(1, room.getCurrentPrice());

            if (room.getCurrentWinner() != null) {
                pstmt.setInt(2, room.getCurrentWinner().getUserId());
            } else {
                pstmt.setNull(2, Types.INTEGER);
            }

            pstmt.setTimestamp(3, Timestamp.valueOf(room.getEndTime()));
            pstmt.setString(4, safeStatus(room).name());
            pstmt.setInt(5, room.getId());

            if (oldPrice != null) {
                pstmt.setBigDecimal(6, oldPrice);
            }

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalStateException("Xung đột dữ liệu: phiên đấu giá đã được cập nhật bởi request khác.");
            }
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM auctions WHERE auction_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy phiên đấu giá để xóa.");
            }
        }
    }

    @Override
    public List<AuctionRoom> findAll() throws Exception {
        List<AuctionRoomRaw> rawList = new ArrayList<>();
        String sql = "SELECT * FROM auctions ORDER BY auction_id DESC";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                rawList.add(readRaw(rs));
            }
        }

        List<AuctionRoom> rooms = new ArrayList<>();
        for (AuctionRoomRaw raw : rawList) {
            AuctionRoom room = buildFromRaw(raw);
            if (room != null) {
                rooms.add(room);
            }
        }

        return rooms;
    }

    @Override
    public List<AuctionRoom> findByStatus(String status) throws Exception {
        List<AuctionRoomRaw> rawList = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ? ORDER BY auction_id DESC";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, normalizeStatus(status).name());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rawList.add(readRaw(rs));
                }
            }
        }

        List<AuctionRoom> rooms = new ArrayList<>();
        for (AuctionRoomRaw raw : rawList) {
            AuctionRoom room = buildFromRaw(raw);
            if (room != null) {
                rooms.add(room);
            }
        }

        return rooms;
    }

    @Override
    public AuctionRoom findById(int id) throws Exception {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";
        AuctionRoomRaw raw = null;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    raw = readRaw(rs);
                }
            }
        }

        return raw != null ? buildFromRaw(raw) : null;
    }

    private AuctionRoomRaw readRaw(ResultSet rs) throws SQLException {
        AuctionRoomRaw raw = new AuctionRoomRaw();

        raw.id = rs.getInt("auction_id");
        raw.itemId = rs.getInt("item_id");
        raw.sellerId = rs.getInt("seller_id");
        raw.currentPrice = rs.getBigDecimal("current_highest_price");
        raw.startTimeTS = rs.getTimestamp("start_time");
        raw.endTimeTS = rs.getTimestamp("end_time");
        raw.statusStr = rs.getString("status");
        raw.winnerId = (Integer) rs.getObject("winner_id");

        return raw;
    }

    private AuctionRoom buildFromRaw(AuctionRoomRaw raw) throws Exception {
        if (raw == null) return null;

        Item item = itemDAO.findById(raw.itemId);
        if (item == null) {
            System.err.println(">>> [AuctionRoomDAO] Bỏ qua auction #" + raw.id + " vì item không tồn tại.");
            return null;
        }

        User winner = null;
        if (raw.winnerId != null) {
            winner = userDAO.findById(raw.winnerId);
        }

        LocalDateTimeSafe time = toSafeTime(raw);

        AuctionRoom room = new AuctionRoom(
                raw.id,
                raw.sellerId,
                item,
                time.startTime,
                time.endTime
        );

        room.setStatus(normalizeStatus(raw.statusStr));

        if (raw.currentPrice != null) {
            room.setCurrentPrice(raw.currentPrice);
        } else if (item.getStartingPrice() != null) {
            room.setCurrentPrice(item.getStartingPrice());
        }

        if (winner != null) {
            room.setCurrentWinner(winner);
        }

        return room;
    }

    private LocalDateTimeSafe toSafeTime(AuctionRoomRaw raw) {
        if (raw.startTimeTS == null || raw.endTimeTS == null) {
            throw new IllegalArgumentException("Auction #" + raw.id + " thiếu start_time hoặc end_time.");
        }

        return new LocalDateTimeSafe(
                raw.startTimeTS.toLocalDateTime(),
                raw.endTimeTS.toLocalDateTime()
        );
    }

    private void validateRoom(AuctionRoom room) {
        if (room == null) {
            throw new IllegalArgumentException("AuctionRoom không được null.");
        }

        if (room.getItem() == null) {
            throw new IllegalArgumentException("AuctionRoom phải có item.");
        }

        if (room.getStarttime() == null || room.getEndTime() == null) {
            throw new IllegalArgumentException("AuctionRoom phải có thời gian bắt đầu và kết thúc.");
        }

        if (!room.getEndTime().isAfter(room.getStarttime())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
    }

    private AuctionStatus safeStatus(AuctionRoom room) {
        return room.getStatus() != null ? room.getStatus() : AuctionStatus.OPEN;
    }

    private AuctionStatus normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return AuctionStatus.OPEN;
        }

        try {
            return AuctionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AuctionStatus.OPEN;
        }
    }

    private static class LocalDateTimeSafe {
        final java.time.LocalDateTime startTime;
        final java.time.LocalDateTime endTime;

        LocalDateTimeSafe(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}