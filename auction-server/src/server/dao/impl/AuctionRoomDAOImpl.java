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

    @Override
    public void insert(AuctionRoom room) throws Exception {
        // CẬP NHẬT: Thêm trường seller_id vào câu lệnh INSERT
        String sql = "INSERT INTO auctions (item_id, start_price, current_highest_price, start_time, end_time, status, winner_id, seller_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
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

            // CẬP NHẬT: Lưu sellerID xuống DB
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

        try (Connection conn = DBConnection.getConnection();
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
        // CẬP NHẬT: Sửa 'id' thành 'auction_id' cho khớp với tên cột trong DB
        String sql = "DELETE FROM auctions WHERE auction_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<AuctionRoom> findAll() throws Exception {
        List<AuctionRoom> rooms = new ArrayList<>();
        String sql = "SELECT * FROM auctions";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                rooms.add(mapResultSetToAuctionRoom(rs));
            }
        }
        return rooms;
    }

    @Override
    public List<AuctionRoom> findByStatus(String status) throws Exception {
        List<AuctionRoom> rooms = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status.toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rooms.add(mapResultSetToAuctionRoom(rs));
                }
            }
        }
        return rooms;
    }

    private AuctionRoom mapResultSetToAuctionRoom(ResultSet rs) throws Exception {
        int id = rs.getInt("auction_id");
        int itemId = rs.getInt("item_id");
        // CẬP NHẬT: Lấy thêm trường seller_id
        int sellerId = rs.getInt("seller_id");

        BigDecimal currentPrice = rs.getBigDecimal("current_highest_price");
        Timestamp startTimeTS = rs.getTimestamp("start_time");
        Timestamp endTimeTs = rs.getTimestamp("end_time");
        String statusStr = rs.getString("status");
        Integer winnerId = (Integer) rs.getObject("winner_id");

        ItemDAO itemDAO = new ItemDAOimpl();
        UserDAO userDAO = new UserDAOimpl();

        Item item = itemDAO.findById(itemId);

        User winner = null;
        if (winnerId != null) {
            winner = userDAO.findById(winnerId);
        }

        // CẬP NHẬT: Sử dụng Constructor 5 tham số (đã bao gồm sellerId)
        AuctionRoom room = new AuctionRoom(
                id,
                sellerId,
                item,
                startTimeTS.toLocalDateTime(),
                endTimeTs.toLocalDateTime()
        );

        room.setStatus(AuctionStatus.valueOf(statusStr));

        // CẬP NHẬT QUAN TRỌNG (FIX LỖI MẤT DỮ LIỆU): Set lại Giá hiện tại và Người thắng từ DB
        if (currentPrice != null) {
            room.setCurrentPrice(currentPrice);
        }
        if (winner != null) {
            room.setCurrentWinner(winner);
        }

        return room;
    }

    @Override
    public AuctionRoom findById(int id) throws Exception {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAuctionRoom(rs);
                }
            }
        }

        return null;
    }
}