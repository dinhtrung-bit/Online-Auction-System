package server.services;

import java.math.BigDecimal;
import java.util.Map;

import server.models.items.Item;

/**
 * Tách riêng phần đọc dữ liệu từ Map client gửi lên.
 * Không đổi rule parse/validate so với ItemService cũ.
 */
public class ItemDataMapper {

    public void applyOptionalClientFields(Item item, Map<String, Object> data) {
        if (item == null || data == null) return;

        if (data.get("imagePath") != null) {
            item.setImagePath(data.get("imagePath").toString());
        }

        if (data.get("bidIncrement") != null) {
            try {
                item.setBidIncrement(new BigDecimal(data.get("bidIncrement").toString()));
            } catch (Exception ignored) {
            }
        }
    }

    public String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;

        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    public int getInt(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return (int) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số nguyên.");
        }
    }

    public BigDecimal getMoney(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        try {
            BigDecimal money = new BigDecimal(value.toString());

            if (money.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Giá tiền phải lớn hơn 0.");
            }

            return money;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số hợp lệ.");
        }
    }
}
