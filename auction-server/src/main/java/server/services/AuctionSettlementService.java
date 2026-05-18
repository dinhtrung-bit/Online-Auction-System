package server.services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;

import server.dao.core.DBConnection;
import server.dao.interfaces.AuctionRoomDAO;
import server.dao.interfaces.UserDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;
import server.models.users.User;

/**
 * Chỉ phụ trách thanh toán khi phiên kết thúc.
 * Logic được giữ nguyên từ AuctionService cũ.
 */
public class AuctionSettlementService {

    private final AuctionRoomDAO roomDAO;
    private final UserDAO userDAO;

    public AuctionSettlementService(AuctionRoomDAO roomDAO, UserDAO userDAO) {
        this.roomDAO = roomDAO;
        this.userDAO = userDAO;
    }

    public void processAuctionSettlement(AuctionRoom room) {
        User winner = room.getCurrentWinner();
        BigDecimal finalPrice = room.getCurrentPrice();

        if (winner == null || finalPrice == null || finalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            room.setStatus(AuctionStatus.CANCELED);
            System.out.println(">>> [Settlement] Room " + room.getId() + " CANCELED: Không có người mua.");
            return;
        }

        try (Connection conn = DBConnection.getInstance()) {
            conn.setAutoCommit(false);
            try {
                User freshWinner = userDAO.findById(winner.getUserId());
                User freshSeller = userDAO.findById(room.getSellerID());

                if (freshWinner == null || freshSeller == null) {
                    throw new IllegalArgumentException("Không tìm thấy winner hoặc seller.");
                }

                if (freshWinner.getAccountBalance().compareTo(finalPrice) < 0) {
                    room.setStatus(AuctionStatus.CANCELED);
                    roomDAO.update(room);
                    conn.commit();
                    System.out.println(">>> [Settlement] Room " + room.getId()
                            + " CANCELED: Winner không đủ số dư.");
                    return;
                }

                subtractBalance(conn, freshWinner.getUserId(), finalPrice);
                addBalance(conn, freshSeller.getUserId(), finalPrice);

                room.setStatus(AuctionStatus.PAID);
                roomDAO.update(room);
                conn.commit();

                System.out.println(">>> [Settlement] Room " + room.getId() + " PAID: "
                        + freshWinner.getUsername() + " trả " + finalPrice);

            } catch (Exception e) {
                conn.rollback();
                room.setStatus(AuctionStatus.CANCELED);
                try {
                    roomDAO.update(room);
                } catch (Exception ignored) {
                    // bỏ qua — đã rollback transaction chính
                }
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            room.setStatus(AuctionStatus.CANCELED);
            System.err.println(">>> [Settlement Error] " + e.getMessage());
        }
    }

    private void subtractBalance(Connection conn, int userId, BigDecimal amount) throws Exception {
        String sql = "UPDATE users SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, userId);
            ps.setBigDecimal(3, amount);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("Số dư không đủ để thanh toán.");
            }
        }
    }

    private void addBalance(Connection conn, int userId, BigDecimal amount) throws Exception {
        String sql = "UPDATE users SET balance = COALESCE(balance, 0) + ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("Không tìm thấy seller để cộng tiền.");
            }
        }
    }
}
