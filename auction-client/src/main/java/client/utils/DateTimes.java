package client.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DateTimes — định dạng ngày giờ cho UI.
 *
 * <p>Tách từ {@code UiUtils} (cũ).
 * Đặt tên class {@code DateTimes} để tránh trùng với {@link DateTimeFormatter} của JDK.
 */
public final class DateTimes {

    private static final DateTimeFormatter UI_PATTERN =
            DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy");

    private DateTimes() {}

    /**
     * Chuyển chuỗi ISO DateTime sang định dạng "HH:mm · dd/MM/yyyy".
     * Trả về "--" nếu chuỗi rỗng, hoặc chuỗi gốc thay 'T' bằng space nếu không parse được.
     */
    public static String format(String iso) {
        if (iso == null || iso.isBlank()) return "--";
        try {
            return LocalDateTime.parse(iso).format(UI_PATTERN);
        } catch (Exception e) {
            return iso.replace('T', ' ');
        }
    }
}
