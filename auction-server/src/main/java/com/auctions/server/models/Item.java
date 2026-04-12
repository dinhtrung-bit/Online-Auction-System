package com.auctions.server.models;
import java.io.Serializable;

public abstract class Item implements Serializable {
    protected String itemId;
    protected String name;
    protected double startingPrice;
    protected String description;
    public Item(String itemId, String name, double startingPrice,String description) {
        this.itemId = itemId;
        this.name = name;
        this.startingPrice = startingPrice;
        this.description=description;
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
public String getDescription(){return description;}
    public abstract String getCategory(); // Trả về thông tin đặc thù từng loại đồ
}



