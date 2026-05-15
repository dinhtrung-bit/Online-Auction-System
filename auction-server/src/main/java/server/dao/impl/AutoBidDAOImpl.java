package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.AutoBidDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AutoBidConfig;
import server.models.users.Bidder;
import server.models.users.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AutoBidDAOImpl implements AutoBidDAO {

    private final UserDAO userDAO;

    public AutoBidDAOImpl(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public void insert(AutoBidConfig autoBid) throws Exception {
        validateAutoBid(autoBid);

        String sql = """
                INSERT INTO auto_bids
                (auction_id, bidder_id, max_bid, increment_step, created_at)
                VALUES (?, ?, ?, ?, NOW())
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, autoBid.getAuctionId());
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
    public void update(AutoBidConfig autoBid) throws Exception {
        validateAutoBid(autoBid);

        String sql = """
                UPDATE auto_bids
                SET auction_id = ?, bidder_id = ?, max_bid = ?, increment_step = ?
                WHERE autobid_id = ?
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, autoBid.getAuctionId());
            pstmt.setInt(2, autoBid.getBidder().getUserId());
            pstmt.setBigDecimal(3, autoBid.getMaxBid());
            pstmt.setBigDecimal(4, autoBid.getIncrement());
            pstmt.setInt(5, autoBid.getId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy cấu hình auto-bid để cập nhật.");
            }
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM auto_bids WHERE autobid_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy cấu hình auto-bid để xóa.");
            }
        }
    }

    @Override
    public AutoBidConfig findById(int id) throws Exception {
        String sql = "SELECT * FROM auto_bids WHERE autobid_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

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
    public List<AutoBidConfig> findAll() throws Exception {
        List<AutoBidConfig> list = new ArrayList<>();

        String sql = "SELECT * FROM auto_bids ORDER BY created_at ASC";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToAutoBid(rs));
            }
        }

        return list;
    }

    @Override
    public List<AutoBidConfig> getAutoBidsByAuctionId(int auctionId) throws Exception {
        List<AutoBidConfig> list = new ArrayList<>();

        String sql = """
                SELECT *
                FROM auto_bids
                WHERE auction_id = ?
                ORDER BY created_at ASC
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, auctionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAutoBid(rs));
                }
            }
        }

        return list;
    }

    @Override
    public AutoBidConfig findByUserIdAndAuctionId(int userId, int auctionId) throws Exception {
        String sql = """
                SELECT *
                FROM auto_bids
                WHERE bidder_id = ? AND auction_id = ?
                """;

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

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
    public void deleteByAuctionIdAndBidderId(int auctionId, int bidderId) throws Exception {
        String sql = "DELETE FROM auto_bids WHERE auction_id = ? AND bidder_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, auctionId);
            pstmt.setInt(2, bidderId);

            pstmt.executeUpdate();
        }
    }

    private AutoBidConfig mapResultSetToAutoBid(ResultSet rs) throws Exception {
        AutoBidConfig autoBid = new AutoBidConfig();

        autoBid.setId(rs.getInt("autobid_id"));
        autoBid.setAuctionId(rs.getInt("auction_id"));

        int bidderId = rs.getInt("bidder_id");
        User user = userDAO.findById(bidderId);

        if (user instanceof Bidder bidder) {
            autoBid.setBidder(bidder);
        } else {
            Bidder fallback = new Bidder();
            fallback.setUserId(bidderId);
            autoBid.setBidder(fallback);
        }

        autoBid.setMaxBid(rs.getBigDecimal("max_bid"));
        autoBid.setIncrement(rs.getBigDecimal("increment_step"));

        Timestamp timestamp = rs.getTimestamp("created_at");
        if (timestamp != null) {
            autoBid.setRegisterTime(timestamp.toLocalDateTime());
        }

        return autoBid;
    }

    private void validateAutoBid(AutoBidConfig autoBid) {
        if (autoBid == null) {
            throw new IllegalArgumentException("AutoBidConfig không được null.");
        }

        if (autoBid.getBidder() == null) {
            throw new IllegalArgumentException("Auto-bid phải có bidder.");
        }

        if (autoBid.getMaxBid() == null || autoBid.getMaxBid().signum() <= 0) {
            throw new IllegalArgumentException("Giá tối đa phải lớn hơn 0.");
        }

        if (autoBid.getIncrement() == null || autoBid.getIncrement().signum() <= 0) {
            throw new IllegalArgumentException("Bước nhảy phải lớn hơn 0.");
        }
    }
}