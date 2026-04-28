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
        String sql = "INSERT INTO auction_rooms (id, item_id, current_price, end_time, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, room.getId());
            // Giả sử Item đã được lưu trước và có ID
            pstmt.setInt(2, room.getItem().getItemId());
            pstmt.setDouble(3, room.getCurrentPrice());
            // Chuyển đổi từ LocalDateTime trong Java sang Timestamp trong SQL
            pstmt.setTimestamp(4, Timestamp.valueOf(room.getEndTime()));
            pstmt.setString(5, room.getStatus().name());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(AuctionRoom room) throws Exception {
        String sql = "UPDATE auction_rooms SET current_price = ?, winner_id = ?, status = ? WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, room.getCurrentPrice());

            // Nếu đã có người thắng thì lưu ID, ngược lại để NULL
            if (room.getCurrentWinner() != null) {
                pstmt.setInt(2, room.getCurrentWinner().getUserId());
            } else {
                pstmt.setNull(2, java.sql.Types.INTEGER);
            }

            pstmt.setString(3, room.getStatus().name());
            pstmt.setLong(4, room.getId());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM auction_rooms WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
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
        Long id = rs.getLong("id");
        int itemId = rs.getInt("item_id");
        double currentPrice = rs.getDouble("current_price");
        Timestamp endTimeTs = rs.getTimestamp("end_time");
        String statusStr = rs.getString("status");

        // Lưu ý: Để có đối tượng Item hoàn chỉnh, bạn có thể cần dùng ItemDAOimpl để lấy thông tin item từ itemId
        // Ở đây tạm thời tạo một Item rỗng hoặc dùng DAO lấy lên
        Item item = null;

        // Khởi tạo phòng đấu giá
        AuctionRoom room = new AuctionRoom(id, item, endTimeTs.toLocalDateTime());
        room.setCurrentPrice(currentPrice);
        room.setStatus(AuctionStatus.valueOf(statusStr));

        return room;
    }
}