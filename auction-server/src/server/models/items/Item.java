package server.models.items;
import java.io.Serializable;
import java.math.BigDecimal;

public abstract class Item implements Serializable {
    //protected Seller seller;
    protected int itemId;
    protected String name;
    protected BigDecimal startingPrice;
   // protected BigDecimal currenthightestprice;
    protected String description;
    public Item(int itemId, String name, BigDecimal startingPrice,String description) {
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

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }
   // public void setCurrenthightestPrice(BigDecimal currenthightestprice){this.currenthightestprice=currenthightestprice;}
    public void setDescription(String description){this.description=description;}

    public int getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }
    //public BigDecimal getCurrenthightestPrice(){return currenthightestprice; }

    public abstract String getCategoryInfo(); // Trả về thông tin đặc thù từng loại đồ
    public String getDescription(){
        return description;
    }
}



