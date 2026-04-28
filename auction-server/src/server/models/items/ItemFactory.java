package server.models.items;

public class ItemFactory {
    // SỬA: Đổi CategoryInfo -> categoryInfo (quy tắc camelCase)
    public static Item createItem(String categoryInfo, int itemId, String name, double startingPrice, String description) {
        // Kiểm tra null hoặc chuỗi rỗng để tránh NullPointerException khi gọi .toUpperCase()
        if (categoryInfo == null || categoryInfo.trim().isEmpty()) {
            throw new IllegalArgumentException("Danh mục sản phẩm không được để trống!");
        }

        switch (categoryInfo.toUpperCase()) {
            case "ART":
                return new Art(itemId, name, startingPrice, description);

            case "ELECTRONIC":
            case "ELECTRONICS": //  Thêm case này dự phòng trường hợp Client gõ nhầm có thêm chữ 'S'
                return new Electronics(itemId, name, startingPrice, description);

            case "VEHICLE":
                // SỬA: Đã đồng bộ thứ tự tham số chuẩn như các Class khác
                return new Vehicle(itemId, name, startingPrice, description);

            default:
                throw new IllegalArgumentException("Loại sản phẩm không hợp lệ: " + categoryInfo);
        }
    }
}