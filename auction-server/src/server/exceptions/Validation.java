package server.exceptions;

import server.models.auction.AuctionStatus;
import server.models.users.User;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public class Validation {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public static void validateEmail(String email) throws IllegalArgumentException {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email không được để trống!");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Định dạng email không hợp lệ!");
        }
    }

    public static void validatePassword(String password) throws IllegalArgumentException {
        if (password == null || password.trim().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự!");
        }
    }

    public static void validateUsername(String username) throws IllegalArgumentException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên đăng nhập không được để trống!");
        }
        if (username.contains(" ")) {
            throw new IllegalArgumentException("Tên đăng nhập không được chứa khoảng trắng!");
        }
    }

    public static BigDecimal validateBidAmount(String amountStr) throws InvalidBidException {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new InvalidBidException("Vui lòng nhập số tiền đặt giá!");
        }
        try {
            BigDecimal amount = new BigDecimal(amountStr.trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidBidException("Số tiền đặt giá phải lớn hơn 0!");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new InvalidBidException("Số tiền đặt giá không đúng định dạng số hợp lệ!");
        }
    }

    // Logic kiểm tra khả năng thanh toán
    public static void validatePaymentAbility(User user, BigDecimal amount) throws Exception {
        if (user.getAccountBalance().compareTo(amount) < 0) {
            throw new Exception("Số dư tài khoản hiện tại không đủ để thực hiện giao dịch này!");
        }
    }

    // Logic chặn việc chuyển đổi trạng thái lung tung
    public static boolean canTransitionTo(AuctionStatus current, AuctionStatus next) {
        switch (current) {
            case OPEN: return next == AuctionStatus.RUNNING || next == AuctionStatus.CANCELED;
            case RUNNING: return next == AuctionStatus.FINISHED;
            case FINISHED: return next == AuctionStatus.PAID || next == AuctionStatus.CANCELED;
            default: return false;
        }
    }
}