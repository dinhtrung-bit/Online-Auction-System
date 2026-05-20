package server.models.items;

import java.math.BigDecimal;

/**
 * Vehicle — lớp con của Item đại diện cho loại sản phẩm là phương tiện.
 */
public class Vehicle extends Item {
    private String brand;
    private int year;
    private String engine;

    /** Constructor 4 tham số — dùng bởi ItemFactory và test. */
    public Vehicle(int itemId, String name, BigDecimal startingPrice, String description) {
        super(itemId, name, startingPrice, description);
        this.brand = "N/A";
        this.year = 0;
        this.engine = "N/A";
    }

    /** Constructor đầy đủ tham số để tránh trường bị null hoặc mặc định. */
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

    // ── Getters & Setters ──

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