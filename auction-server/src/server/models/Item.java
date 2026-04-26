package server.models;
import java.io.Serializable;

public abstract class Item implements Serializable {
    //protected Seller seller;
    protected int itemId;
    protected String name;
    protected double startingPrice;
    protected String description;
    public Item(int itemId, String name, double startingPrice,String description) {
        this.itemId = itemId;
        this.name = name;
        this.startingPrice = startingPrice;
        this.description=description;
      //  this.seller=seller;
    }
    //public Seller getseller(){ return seller;}

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }
    public void setDescription(String description){this.description=description;}

    public int getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public abstract String getCategoryInfo(); // Trả về thông tin đặc thù từng loại đồ
    public String getDescription(){
        return description;
    }
}



