package client.utils;

/**
 * StatusMapper — chuyển đổi mã trạng thái phiên đấu giá / danh mục
 * sang nhãn tiếng Việt, badge text và CSS style.
 *
 * <p>Tập trung mọi mapping trạng thái rải rác trong các controllers
 * (AuctionListController, AuctionDetailController, SellerDashboardController)
 * vào một nơi duy nhất.
 */
public final class StatusMapper {

    // ─── Inline style hằng số cho badge ─────────────────────────────
    private static final String BADGE_BASE =
            "-fx-padding:5 10; -fx-background-radius:999; -fx-font-size:11px; -fx-font-weight:900;";

    private StatusMapper() {}

    // ─── Vietnamese status ──────────────────────────────────────────

    /** Dịch mã trạng thái sang tiếng Việt cho hiển thị chung. */
    public static String toVietnamese(String status) {
        if (status == null) return "Không rõ";
        return switch (status.toUpperCase()) {
            case "RUNNING"  -> "Đang đấu giá";
            case "OPEN"     -> "Sắp bắt đầu";
            case "FINISHED" -> "Kết thúc";
            case "PAID"     -> "Đã thanh toán";
            case "CANCELED", "CANCELLED" -> "Đã hủy";
            default         -> status;
        };
    }

    /** Phiên bản kèm icon cho bảng Seller. */
    public static String toSellerText(String status) {
        if (status == null) return "📦 Chưa đăng";
        return switch (status.toUpperCase()) {
            case "OPEN"     -> "⏳ Sắp bắt đầu";
            case "RUNNING"  -> "🔴 Đang đấu giá";
            case "FINISHED" -> "✅ Kết thúc";
            case "PAID"     -> "💰 Đã thanh toán";
            case "CANCELED", "CANCELLED" -> "❌ Đã hủy";
            default         -> "📦 Chưa đăng";
        };
    }

    /** Kiểm tra trạng thái đã kết thúc (không cho đặt giá). */
    public static boolean isTerminal(String status) {
        if (status == null) return false;
        return switch (status.trim().toUpperCase()) {
            case "FINISHED", "PAID", "CANCELED", "CANCELLED",
                 "KẾT THÚC", "ĐÃ THANH TOÁN", "ĐÃ HỦY" -> true;
            default -> false;
        };
    }

    // ─── Category ───────────────────────────────────────────────────

    /** Chuẩn hoá mã danh mục sang tên tiếng Việt. */
    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "--";
        return switch (category.toUpperCase()) {
            case "ART"                       -> "Nghệ thuật";
            case "ELECTRONIC", "ELECTRONICS" -> "Đồ điện tử";
            case "VEHICLE"                   -> "Phương tiện";
            default                          -> category;
        };
    }

    // ─── Card badge (AuctionList) ───────────────────────────────────

    /** Text hiển thị trong badge card phiên đấu giá. */
    public static String cardBadgeText(String status) {
        if (status == null) return "";
        return switch (status.toUpperCase()) {
            case "OPEN"     -> "🕐 Sắp diễn ra";
            case "RUNNING"  -> "● Đang diễn ra";
            case "FINISHED" -> "✓ Kết thúc";
            case "CANCELED", "CANCELLED" -> "✗ Đã hủy";
            case "PAID"     -> "💳 Đã thanh toán";
            default         -> status;
        };
    }

    /** Inline-style cho badge card. */
    public static String cardBadgeStyle(String status) {
        if (status == null) return BADGE_BASE;
        String tail = switch (status.toUpperCase()) {
            case "RUNNING"  -> " -fx-background-color:#dcfce7; -fx-text-fill:#166534;";
            case "FINISHED" -> " -fx-background-color:#f1f5f9; -fx-text-fill:#64748b;";
            case "CANCELED", "CANCELLED" -> " -fx-background-color:#fee2e2; -fx-text-fill:#991b1b;";
            case "PAID"     -> " -fx-background-color:#dbeafe; -fx-text-fill:#0c4a6e;";
            default         -> " -fx-background-color:#fef9c3; -fx-text-fill:#854d0e;";
        };
        return BADGE_BASE + tail;
    }

    /** Icon cho box ảnh card. */
    public static String cardIcon(String status) {
        if (status == null) return "📦";
        return switch (status.toUpperCase()) {
            case "RUNNING" -> "🔥";
            case "OPEN"    -> "⏳";
            case "FINISHED", "PAID" -> "🏆";
            default        -> "📦";
        };
    }

    // ─── Seller status badge style ──────────────────────────────────

    /** Style cho badge trong bảng SellerDashboard (sau khi đã text-hoá). */
    public static String sellerBadgeStyle(String sellerText) {
        String base = "-fx-padding: 3 8; -fx-background-radius: 5; -fx-font-weight: bold; -fx-font-size: 11px;";
        if (sellerText == null) return base;
        return base + switch (sellerText) {
            case "🔴 Đang đấu giá" -> "-fx-background-color: #dcfce7; -fx-text-fill: #166534;";
            case "⏳ Sắp bắt đầu"   -> "-fx-background-color: #fef9c3; -fx-text-fill: #854d0e;";
            case "✅ Kết thúc"      -> "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";
            case "💰 Đã thanh toán"-> "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af;";
            case "❌ Đã hủy"        -> "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
            default                -> "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";
        };
    }
}
