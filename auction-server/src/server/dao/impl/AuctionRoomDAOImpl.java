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
        String sql = "INSERT INTO auctions ( item_id,start_price, current_highest_price,start_time, end_time, status,winner_id) VALUES (?, ?, ?, ?, ?,?,?)";

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

            pstmt.executeUpdate();
        }
    }

    // Hàm update 1 tham số bắt buộc từ GenericDAO
    // (Dùng cho các trường hợp không cần lock, ví dụ cập nhật trạng thái)
    @Override
    public void update(AuctionRoom room) throws Exception {
        // Gọi lại hàm update 2 tham số với oldPrice chính là currentPrice hiện tại
        this.update(room, room.getCurrentPrice());
    }

    // Hàm update 2 tham số chuyên dụng hỗ trợ Optimistic Lock khi đặt giá
    @Override
    public void update(AuctionRoom room, BigDecimal oldPrice) throws Exception {
        // Tách câu lệnh SQL: Nếu chưa có ai đặt (oldPrice = null) thì phải check IS NULL
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

            // Nếu có giá cũ thì truyền vào tham số thứ 6 cho điều kiện WHERE = ?
            if (oldPrice != null) {
                pstmt.setBigDecimal(6, oldPrice);
            }

            int rowsAffected = pstmt.executeUpdate();

            // Cơ chế Optimistic Lock chuẩn mực: 0 rows affected nghĩa là sai giá trị version/oldPrice
            if (rowsAffected == 0) {
                throw new Exception("Xung đột dữ liệu (Lost Update): Đã có người khác đặt giá cao hơn trong tích tắc!");
            }
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM auctions WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<AuctionRoom> findAll() throws Exception {
        List<AuctionRoom> rooms = new ArrayList<>();
        String sql = "SELECT * FROM auctions";

        try (Connection conn = DBConnection.getInstance().getConnection();
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

        try (Connection conn = DBConnection.getInstance().getConnection();
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
        // Đã sửa 'aution_id' thành 'auction_id'
        int id = rs.getInt("auction_id");
        int itemId = rs.getInt("item_id");
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

        AuctionRoom room = new AuctionRoom(
                id,
                item,
                startTimeTS.toLocalDateTime(),
                endTimeTs.toLocalDateTime()
        );

        room.setStatus(AuctionStatus.valueOf(statusStr));

        return room;
    }

    @Override
    public AuctionRoom findById(int id) throws Exception {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
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