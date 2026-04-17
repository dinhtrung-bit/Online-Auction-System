<<<<<<< HEAD
package server.models;
=======
package src.server.models;
>>>>>>> e6fdc0c29820aeb4b000a79a18715e91b6bec51e
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



