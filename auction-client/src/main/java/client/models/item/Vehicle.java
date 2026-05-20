
package client.models.item;

/**
 * Vehicle — client model cho sản phẩm loại phương tiện.
 * File này bị thiếu hoàn toàn, khiến mọi item VEHICLE từ server
 * bị fallback thành Art và hiển thị sai danh mục.
 */
public class Vehicle extends Item {

    public Vehicle(String itemId, String name, double startingPrice) {
        super(itemId, name, startingPrice);
    }

    @Override
    public String getDetails() {
        String desc = getDescription();
        return (desc == null || desc.isBlank()) ? "Chưa có mô tả" : desc;
    }
}
