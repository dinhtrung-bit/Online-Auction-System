package server.networks.handlers;

import com.google.gson.Gson;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import server.models.users.Bidder;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.AuctionService;

/**
 * AutoBidRequestHandler — Xử lý request đặt và hủy auto-bid.
 *
 * <p>Class này chỉ gồm hai endpoint public, hai method parse riêng và một record payload.
 * Mọi utility parse/clean số đều dùng chung qua {@link PayloadParser}.
 */
public class AutoBidRequestHandler {

    private static final BigDecimal DEFAULT_STEP = new BigDecimal("500");
    private static final Gson GSON = new Gson();

    private final AuctionService auctionService;

    public AutoBidRequestHandler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    // ─── Public handlers ─────────────────────────────────────────────────────

    public MessageDTO handleSetAutoBid(MessageDTO request, User loggedInUser) {
        if (!(loggedInUser instanceof Bidder bidder)) {
            return new MessageDTO("SET_AUTO_BID_FAILED", "Chỉ Bidder mới được đặt auto-bid!");
        }
        try {
            AutoBidPayload p = parseSetAutoBidPayload(request);
            auctionService.registerAutoBid(p.auctionId(), bidder, p.maxBid(), p.step());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("auctionId", p.auctionId());
            response.put("userId", bidder.getUserId());
            response.put("username", bidder.getUsername());
            response.put("message", "Đặt auto bid thành công!");

            return new MessageDTO("SET_AUTO_BID_SUCCESS", GSON.toJson(response));
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

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("auctionId", auctionId);
            response.put("userId", loggedInUser.getUserId());
            response.put("username", loggedInUser.getUsername());
            response.put("message", "Hủy auto bid thành công!");

            return new MessageDTO("CANCEL_AUTO_BID_SUCCESS", GSON.toJson(response));

        } catch (IllegalArgumentException e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("CANCEL_AUTO_BID_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    // ─── Parse payload ───────────────────────────────────────────────────────

    private AutoBidPayload parseSetAutoBidPayload(MessageDTO request) {
        String payload = PayloadParser.requirePayload(request);

        if (payload.startsWith("{")) {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);

            int auctionId = PayloadParser.hasKey(data, "auctionId")
                    ? PayloadParser.getInt(data, "auctionId")
                    : PayloadParser.getInt(data, "roomId");

            BigDecimal maxBid;
            if (PayloadParser.hasKey(data, "maxBid")) {
                maxBid = PayloadParser.getBigDecimal(data, "maxBid");
            } else if (PayloadParser.hasKey(data, "max")) {
                maxBid = PayloadParser.getBigDecimal(data, "max");
            } else if (PayloadParser.hasKey(data, "amount")) {
                maxBid = PayloadParser.getBigDecimal(data, "amount");
            } else {
                throw new IllegalArgumentException("Thiếu maxBid.");
            }

            BigDecimal step;
            if (PayloadParser.hasKey(data, "step")) {
                step = PayloadParser.getBigDecimal(data, "step");
            } else if (PayloadParser.hasKey(data, "increment")) {
                step = PayloadParser.getBigDecimal(data, "increment");
            } else if (PayloadParser.hasKey(data, "bidIncrement")) {
                step = PayloadParser.getBigDecimal(data, "bidIncrement");
            } else {
                step = DEFAULT_STEP;
            }

            return new AutoBidPayload(auctionId, maxBid, step);
        }

        // Legacy format: "auctionId:maxBid:step"
        String[] parts = payload.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Payload auto-bid không hợp lệ.");
        }

        int auctionId = (int) Double.parseDouble(PayloadParser.cleanNumberText(parts[0]));
        BigDecimal maxBid = new BigDecimal(PayloadParser.cleanNumberText(parts[1]));
        BigDecimal step = parts.length >= 3
                ? new BigDecimal(PayloadParser.cleanNumberText(parts[2]))
                : DEFAULT_STEP;

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

    private record AutoBidPayload(int auctionId, BigDecimal maxBid, BigDecimal step) {}
}