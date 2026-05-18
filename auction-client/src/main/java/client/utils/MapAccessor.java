package client.utils;

import java.util.Map;

/**
 * MapAccessor — đọc giá trị từ {@code Map<String, Object>} (payload JSON
 * đã được Gson parse) một cách an toàn, có fallback.
 *
 * <p>Dùng chung trong toàn bộ controllers thay vì lặp lại
 * {@code map.get(key) != null ? Number/String/Double.parseDouble(...) : fallback}.
 */
public final class MapAccessor {

    private MapAccessor() {}

    /** Lấy String, fallback nếu null hoặc rỗng. */
    public static String getString(Map<String, Object> map, String key, String fallback) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return fallback;
        String value = String.valueOf(map.get(key));
        return "null".equalsIgnoreCase(value) ? fallback : value;
    }

    /** Lấy String, fallback "" nếu không có. */
    public static String getString(Map<String, Object> map, String key) {
        return getString(map, key, "");
    }

    /** Lấy double, parse linh hoạt Number/String. */
    public static double getDouble(Map<String, Object> map, String key, double fallback) {
        if (map == null) return fallback;
        return SafeParser.numberFrom(map.get(key), fallback);
    }

    /** Lấy double, fallback 0. */
    public static double getDouble(Map<String, Object> map, String key) {
        return getDouble(map, key, 0);
    }

    /** Lấy int, làm tròn từ double. */
    public static int getInt(Map<String, Object> map, String key, int fallback) {
        return (int) Math.round(getDouble(map, key, fallback));
    }

    /** Lấy int, fallback 0. */
    public static int getInt(Map<String, Object> map, String key) {
        return getInt(map, key, 0);
    }

    /** Lấy long, làm tròn từ double. */
    public static long getLong(Map<String, Object> map, String key, long fallback) {
        return Math.round(getDouble(map, key, fallback));
    }
}
