<<<<<<< HEAD
<<<<<<< HEAD
package src.server.models;
=======
package server.models;
>>>>>>> dinhtrung
=======
package src.server.models;
>>>>>>> thắng
import java.io.Serializable;

public abstract class Item implements Serializable {
    protected String itemId;
    protected String name;
    protected double startingPrice;
<<<<<<< HEAD

    public Item(String itemId, String name, double startingPrice) {
        this.itemId = itemId;
        this.name = name;
        this.startingPrice = startingPrice;
=======
    protected String description;
    public Item(String itemId, String name, double startingPrice,String description) {
        this.itemId = itemId;
        this.name = name;
        this.startingPrice = startingPrice;
        this.description=description;
>>>>>>> thắng
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
<<<<<<< HEAD

    public abstract String getCategoryInfo(); // Trả về thông tin đặc thù từng loại đồ
=======
public String getDescription(){return description;}
    public abstract String getCategory(); // Trả về thông tin đặc thù từng loại đồ
>>>>>>> thắng
}



