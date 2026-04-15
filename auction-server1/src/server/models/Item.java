package server.models;
import java.io.Serializable;

public abstract class Item implements Serializable {
    protected String itemId;
    protected String name;
    protected double startingPrice;

    public Item(String itemId, String name, double startingPrice) {
        this.itemId = itemId;
        this.name = name;
        this.startingPrice = startingPrice;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public String getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public abstract String getCategoryInfo(); // Trả về thông tin đặc thù từng loại đồ
}

class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String itemId, String name, double startingPrice, int warranty) {
        super(itemId, name, startingPrice);
        this.warrantyMonths = warranty;
    }

    @Override
    public String getCategoryInfo() {
        return "Điện tử - Bảo hành: " + warrantyMonths + " tháng";
    }
}

class Art extends Item {
    private String artist;

    public Art(String itemId, String name, double startingPrice, String artist) {
        super(itemId, name, startingPrice);
        this.artist = artist;
    }

    @Override
    public String getCategoryInfo() {
        return "Nghệ thuật - Tác giả: " + artist;
    }
}