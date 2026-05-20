package server.models.items;

import java.math.BigDecimal;

public class ItemFactory {

    public static Item createItem(String categoryInfo, int itemId, String name, BigDecimal startingPrice, String description) {
        if (categoryInfo == null || categoryInfo.trim().isEmpty()) {
            throw new IllegalArgumentException("Danh mục sản phẩm không được để trống!");
        }

        ItemCategory category;
        try {
            String normalizedCategory = categoryInfo.trim().toUpperCase();
            // Dự phòng trường hợp Client gửi thiếu chữ 'S'
            if (normalizedCategory.equals("ELECTRONIC")) {
                normalizedCategory = "ELECTRONICS";
            }
            // Chuyển đổi String thành Enum
            category = ItemCategory.valueOf(normalizedCategory);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Loại sản phẩm không hợp lệ: " + categoryInfo);
        }

        // Switch trực tiếp trên Enum
        switch (category) {
            case ART:
                return new Art(itemId, name, startingPrice, description);

            case ELECTRONICS:
                return new Electronics(itemId, name, startingPrice, description);


            case VEHICLE:
                return new Vehicle(itemId, name, startingPrice, description);

            default:
                throw new IllegalArgumentException("Danh mục chưa được hỗ trợ: " + category);
        }
    }
}