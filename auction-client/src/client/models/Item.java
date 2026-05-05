package client.models;

import java.io.Serializable;

public abstract class Item implements Serializable {
    private static final long serialVersionUID = 1L;

    protected String itemId;
    protected String name;
    protected double startingPrice;

    // THÊM 3 THUỘC TÍNH MỚI:
    protected double bidIncrement; // Bước nhảy
    protected int durationMinutes; // Thời gian đếm ngược (Phút)
    protected String imagePath;    // Đường dẫn ảnh minh họa

    public Item(String itemId, String name, double startingPrice) {
        this.itemId = itemId;
        this.name = name;
        this.startingPrice = startingPrice;
    }

    // Các Getter cũ...
    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public double getStartingPrice() { return startingPrice; }

    // Thêm Getter/Setter cho các thuộc tính mới
    public double getBidIncrement() { return bidIncrement; }
    public void setBidIncrement(double bidIncrement) { this.bidIncrement = bidIncrement; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public abstract String getDetails();
}