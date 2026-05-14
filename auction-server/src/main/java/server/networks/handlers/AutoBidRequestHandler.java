package server.networks.handlers;

import server.models.users.Bidder;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.AuctionService;

import java.math.BigDecimal;

/**
 * AutoBidRequestHandler — xử lý SET_AUTO_BID và CANCEL_AUTO_BID.
 * Chỉ biết đến AuctionService — không gọi AutoBidDAO trực tiếp.
 */
public class AutoBidRequestHandler {

    private final AuctionService auctionService;

    public AutoBidRequestHandler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public MessageDTO handleSetAutoBid(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        if (!loggedInUser.canBid())
            return new MessageDTO("SET_AUTO_BID_FAILED", "Chỉ Bidder mới được đặt auto-bid!");
        try {
            String[] data = request.getPayload().split(":");
            int auctionId    = Integer.parseInt(data[0]);
            BigDecimal max   = new BigDecimal(data[1]);
            BigDecimal step  = new BigDecimal(data[2]);

            auctionService.registerAutoBid(auctionId, (Bidder) loggedInUser, max, step);
            return new MessageDTO("SET_AUTO_BID_SUCCESS", "Đặt auto bid thành công!");
        } catch (Exception e) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleCancelAutoBid(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            int auctionId = Integer.parseInt(request.getPayload().trim());
            int userId = loggedInUser.getUserId();
            auctionService.cancelAutoBid(auctionId ,userId);
            return new MessageDTO("CANCEL_AUTO_BID_SUCCESS", "Hủy auto bid thành công!");
        } catch (Exception e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Lỗi: " + e.getMessage());
        }
    }
}