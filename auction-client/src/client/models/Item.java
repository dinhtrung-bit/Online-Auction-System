package client.models;

import java.io.Serializable;

// Lớp cha trừu tượng
public abstract class Item implements Serializable {
    private static final long serialVersionUID = 1L;

    protected String itemId;
    protected String name;
    protected double startingPrice;

    public Item(String itemId, String name, double startingPrice) {
        this.itemId = itemId;
        this.name = name;
        this.startingPrice = startingPrice;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public double getStartingPrice() { return startingPrice; }

    // Phương thức trừu tượng để thể hiện tính đa hình
    public abstract String getDetails();
}

// Lớp con Art
class Art extends Item {
    private String artist;

    public Art(String itemId, String name, double startingPrice, String artist) {
        super(itemId, name, startingPrice);
        this.artist = artist;
    }

    @Override
    public String getDetails() {
        return "Tác phẩm nghệ thuật bởi: " + artist;
    }
}

// Lớp con Electronics
class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String itemId, String name, double startingPrice, int warrantyMonths) {
        super(itemId, name, startingPrice);
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public String getDetails() {
        return "Hàng điện tử - Bảo hành: " + warrantyMonths + " tháng";
    }
}