package server.dao.impl;

import server.dao.interfaces.AuctionRoomDAO;
import server.dao.core.DBConnection;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.items.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class AuctionRoomDAOImpl implements AuctionRoomDAO {

    @Override
    public void insert(AuctionRoom room) throws Exception {
        String sql = "INSERT INTO auctions ( item_id, current_highest_price,start_time, end_time, status,winner_id) VALUES (?, ?, ?, ?, ?,?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Giả sử Item đã được lưu trước và có ID
            pstmt.setInt(1, room.getItem().getItemId());
            pstmt.setDouble(2, room.getCurrentPrice());
            // Chuyển đổi từ LocalDateTime trong Java sang Timestamp trong SQL
            pstmt.setTimestamp(3, Timestamp.valueOf(room.getStarttime()));
            pstmt.setTimestamp(4, Timestamp.valueOf(room.getEndTime()));
            pstmt.setString(5, room.getStatus().name());
            pstmt.setInt(6, room.getCurrentWinner().getUserId());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(AuctionRoom room) throws Exception {
        // Thêm start_time và end_time vào câu lệnh SQL
        String sql = "UPDATE auctions SET current_highest_price = ?, winner_id = ?, end_time = ?, status = ? WHERE auction_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, room.getCurrentPrice());

            // Winner tạm thời
            if (room.getCurrentWinner() != null) {
                pstmt.setInt(2, room.getCurrentWinner().getUserId());
            } else {
                pstmt.setNull(2, java.sql.Types.INTEGER);
            }
            pstmt.setTimestamp(3, Timestamp.valueOf(room.getEndTime()));

            pstmt.setString(4, room.getStatus().name());
            pstmt.setLong(5, room.getId());

            pstmt.executeUpdate();

        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM auction_rooms WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<AuctionRoom> findAll() throws Exception {
        List<AuctionRoom> rooms = new ArrayList<>();
        String sql = "SELECT * FROM auction_rooms";

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
        String sql = "SELECT * FROM auction_rooms WHERE status = ?";

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

    // Hàm hỗ trợ map dữ liệu từ DB sang Object (Giúp tránh lặp code)
    private AuctionRoom mapResultSetToAuctionRoom(ResultSet rs) throws Exception {
        int id = rs.getInt("id");
        int itemId = rs.getInt("item_id");
        double currentPrice = rs.getDouble("current_price");
        Timestamp startTimeTS = rs.getTimestamp("start_time");
        Timestamp endTimeTs = rs.getTimestamp("end_time");
        String statusStr = rs.getString("status");

        // Lưu ý: Để có đối tượng Item hoàn chỉnh, bạn có thể cần dùng ItemDAOimpl để lấy thông tin item từ itemId
        // Ở đây tạm thời tạo một Item rỗng hoặc dùng DAO lấy lên
        Item item = null;

        // Khởi tạo phòng đấu giá
        AuctionRoom room = new AuctionRoom(id, item,startTimeTS.toLocalDateTime(),endTimeTs.toLocalDateTime());
        room.setCurrentPrice(currentPrice);
        room.setStatus(AuctionStatus.valueOf(statusStr));

        return room;
    }
}