package server.networks.handlers;

import server.models.users.Bidder;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.AuctionService;

import java.math.BigDecimal;
import java.util.Map;

/**
 * AutoBidRequestHandler — Xử lý request đặt/hủy auto-bid.
 *
 * Sau refactor:
 *   - Chỉ chứa 2 handler public + 2 method parse riêng
 *   - Utility parse/clean số → PayloadParser (dùng chung với AuctionRequestHandler)
 */
public class AutoBidRequestHandler {

    private final AuctionService auctionService;

    public AutoBidRequestHandler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public handlers
    // ─────────────────────────────────────────────────────────────────────────

    public MessageDTO handleSetAutoBid(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Bidder bidder)) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Chỉ Bidder mới được đặt auto-bid!");
        }
        try {
            AutoBidPayload p = parseSetAutoBidPayload(request);

            auctionService.registerAutoBid(p.auctionId(), bidder, p.maxBid(), p.step());

            return new MessageDTO("SET_AUTO_BID_SUCCESS", "Đặt auto bid thành công!");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("SET_AUTO_BID_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleCancelAutoBid(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Chưa đăng nhập.");
        }
        try {
            int auctionId = parseAuctionId(request);

            auctionService.cancelAutoBid(auctionId, loggedInUser.getUserId());

            return new MessageDTO("CANCEL_AUTO_BID_SUCCESS", "Hủy auto bid thành công!");

        } catch (IllegalArgumentException e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parse payload (logic đặc thù của AutoBid)
    // ─────────────────────────────────────────────────────────────────────────

    private AutoBidPayload parseSetAutoBidPayload(MessageDTO request) {
        String payload = PayloadParser.requirePayload(request);

        if (payload.startsWith("{")) {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);

            int auctionId = PayloadParser.hasKey(data, "auctionId")
                    ? PayloadParser.getInt(data, "auctionId")
                    : PayloadParser.getInt(data, "roomId");

            BigDecimal maxBid;
            if      (PayloadParser.hasKey(data, "maxBid"))  maxBid = PayloadParser.getBigDecimal(data, "maxBid");
            else if (PayloadParser.hasKey(data, "max"))     maxBid = PayloadParser.getBigDecimal(data, "max");
            else if (PayloadParser.hasKey(data, "amount"))  maxBid = PayloadParser.getBigDecimal(data, "amount");
            else throw new IllegalArgumentException("Thiếu maxBid.");

            BigDecimal step;
            if      (PayloadParser.hasKey(data, "step"))         step = PayloadParser.getBigDecimal(data, "step");
            else if (PayloadParser.hasKey(data, "increment"))    step = PayloadParser.getBigDecimal(data, "increment");
            else if (PayloadParser.hasKey(data, "bidIncrement")) step = PayloadParser.getBigDecimal(data, "bidIncrement");
            else step = new BigDecimal("500");

            PayloadParser.validatePositive(maxBid, "maxBid");
            PayloadParser.validatePositive(step, "step");

            return new AutoBidPayload(auctionId, maxBid, step);
        }

        // Legacy format: "auctionId:maxBid:step"
        String[] parts = payload.split(":");
        if (parts.length < 2) throw new IllegalArgumentException("Payload auto-bid không hợp lệ.");

        int        auctionId = (int) Double.parseDouble(PayloadParser.cleanNumberText(parts[0]));
        BigDecimal maxBid    = new BigDecimal(PayloadParser.cleanNumberText(parts[1]));
        BigDecimal step      = parts.length >= 3
                ? new BigDecimal(PayloadParser.cleanNumberText(parts[2]))
                : new BigDecimal("500");

        PayloadParser.validatePositive(maxBid, "maxBid");
        PayloadParser.validatePositive(step, "step");

        return new AutoBidPayload(auctionId, maxBid, step);
    }

    private int parseAuctionId(MessageDTO request) {
        String payload = PayloadParser.requirePayload(request);

        if (payload.startsWith("{")) {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);

            return PayloadParser.hasKey(data, "auctionId")
                    ? PayloadParser.getInt(data, "auctionId")
                    : PayloadParser.getInt(data, "roomId");
        }

        try {
            return (int) Double.parseDouble(PayloadParser.cleanNumberText(payload));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("auctionId phải là số nguyên.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payload DTO
    // ─────────────────────────────────────────────────────────────────────────

    private record AutoBidPayload(int auctionId, BigDecimal maxBid, BigDecimal step) {}
}