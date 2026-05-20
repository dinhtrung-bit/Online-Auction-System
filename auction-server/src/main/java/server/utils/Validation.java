package server.utils;

import server.models.auction.AuctionStatus;

import java.util.regex.Pattern;

/**
 * Validation — utility tĩnh cho các quy tắc validate đơn giản.
 *
 * Đã xóa:
 *   - validateEmail(): không được gọi trong register flow.
 *   - validateBidAmount(): handler dùng PayloadParser, service dùng BigDecimal trực tiếp.
 *   - validatePaymentAbility(): AuctionService và User.hasEnoughBalance() xử lý.
 *
 * Giữ lại:
 *   - validateUsername() / validatePassword(): được gọi trong UserService.register().
 *   - canTransitionTo(): được tích hợp vào AuctionService.processRoomStatusTick()
 *     thay vì để service tự hard-code điều kiện chuyển trạng thái.
 */
public final class Validation {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,30}$");

    private Validation() {}

    public static void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên đăng nhập không được để trống.");
        }
        if (!USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Tên đăng nhập chỉ được chứa chữ cái, số, dấu gạch dưới (3–30 ký tự).");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự.");
        }
    }

    /**
     * Kiểm tra chuyển trạng thái phòng đấu giá có hợp lệ không.
     * Được gọi trong AuctionService.processRoomStatusTick() để tập trung
     * state machine logic thay vì hard-code điều kiện rải rác.
     *
     * Sơ đồ hợp lệ:
     *   OPEN → RUNNING | CANCELED
     *   RUNNING → FINISHED | CANCELED
     *   FINISHED → PAID | CANCELED
     *   PAID / CANCELED → (terminal, không đi đâu)
     */
    public static boolean canTransitionTo(AuctionStatus current, AuctionStatus next) {
        if (current == null || next == null) return false;
        return switch (current) {
            case OPEN     -> next == AuctionStatus.RUNNING  || next == AuctionStatus.CANCELED;
            case RUNNING  -> next == AuctionStatus.FINISHED || next == AuctionStatus.CANCELED;
            case FINISHED -> next == AuctionStatus.PAID     || next == AuctionStatus.CANCELED;
            case PAID, CANCELED -> false; // terminal states
        };
    }
}
