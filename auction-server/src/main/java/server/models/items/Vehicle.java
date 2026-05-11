package server.models.items;

import java.math.BigDecimal;

public class Vehicle extends Item {
    // Các thuộc tính riêng (đang có sẵn)
    private String brand;
    private int year;
    private String engine;

    // Đã SỬA: Đảo lại thứ tự (double startingPrice) lên trước (String description)
    public Vehicle(int itemId, String name, BigDecimal startingPrice,String description) {
        super(itemId, name, startingPrice, description);
    }

    @Override
    public String getCategoryInfo() {
        return "VEHICLE";
    }
}