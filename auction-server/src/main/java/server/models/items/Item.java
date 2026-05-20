package server.models.items;

import server.models.users.Seller;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Item — lớp trừu tượng đại diện cho sản phẩm đấu giá.
 *
 * Sửa lỗi: constructor cũ gọi this.seller = seller nhưng seller chưa được
 * truyền vào tham số → luôn null. Đã loại bỏ seller khỏi constructor,
 * dùng setSeller() riêng (DAO vẫn làm vậy sau khi tạo object).
 *
 * getCategoryInfo() là abstract method buộc subclass xác định danh mục —
 * được dùng trong ItemFactory, DAO và AuctionMapper.
 */
public abstract class Item implements Serializable {

    private static final long serialVersionUID = 1L;

    private int itemId;
    private String name;
    private String description;
    private BigDecimal startingPrice;
    private BigDecimal bidIncrement = BigDecimal.ZERO;
    private String imagePath;
    private Seller seller;

    protected Item(int itemId, String name, BigDecimal startingPrice, String description) {
        this.itemId = itemId;
        this.name = name != null ? name : "";
        this.startingPrice = startingPrice != null ? startingPrice : BigDecimal.ZERO;
        this.description = description != null ? description : "";
    }

    /** Trả về chuỗi danh mục (ART, ELECTRONIC, VEHICLE). Dùng trong DAO và AuctionMapper. */
    public abstract String getCategoryInfo();

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getItemId()              { return itemId; }
    public String getName()             { return name; }
    public String getDescription()      { return description; }
    public BigDecimal getStartingPrice(){ return startingPrice; }
    public BigDecimal getBidIncrement() { return bidIncrement != null ? bidIncrement : BigDecimal.ZERO; }
    public String getImagePath()        { return imagePath != null ? imagePath : ""; }
    public Seller getSeller()           { return seller; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setItemId(int itemId)                  { this.itemId = itemId; }
    public void setName(String name)                   { this.name = name != null ? name : ""; }
    public void setDescription(String desc)            { this.description = desc != null ? desc : ""; }
    public void setStartingPrice(BigDecimal price)     { this.startingPrice = price != null ? price : BigDecimal.ZERO; }
    public void setBidIncrement(BigDecimal increment)  { this.bidIncrement = increment != null ? increment : BigDecimal.ZERO; }
    public void setImagePath(String imagePath)         { this.imagePath = imagePath; }
    public void setSeller(Seller seller)               { this.seller = seller; }
}
