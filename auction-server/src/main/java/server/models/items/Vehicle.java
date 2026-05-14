package server.models.items;

import java.math.BigDecimal;

/**
 * Vehicle — lớp con của Item đại diện cho loại sản phẩm là phương tiện.
 * * Fix 3.8 (Dead fields):
 * Đã gán giá trị cho brand, year, engine trong constructor.
 * Thêm getter/setter để các lớp khác (DAO, UI) có thể truy cập dữ liệu.
 */
public class Vehicle extends Item {
    private String brand;
    private int year;
    private String engine;

    // Constructor đầy đủ tham số để tránh trường bị null hoặc mặc định
    public Vehicle(int itemId, String name, BigDecimal startingPrice, String description,
                   String brand, int year, String engine) {
        // Gọi constructor của lớp cha (Item)
        super(itemId, name, startingPrice, description);
        this.brand = brand;
        this.year = year;
        this.engine = engine;
    }

    @Override
    public String getCategoryInfo() {
        return "VEHICLE";
    }

    // ── Getters & Setters (Bổ sung để xử lý lỗi không truy cập được dữ liệu) ──

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}