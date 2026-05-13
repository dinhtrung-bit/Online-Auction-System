package server.services;

import server.dao.impl.ItemDAOImpl;
import server.dao.interfaces.ItemDAO;
import server.models.items.Item;
import server.models.items.ItemFactory;
import server.models.users.Seller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ItemService — xử lý toàn bộ nghiệp vụ liên quan đến sản phẩm.
 * ClientHandler KHÔNG gọi ItemDAO trực tiếp — mọi thứ đi qua đây.
 *
 * Constructor Injection: nhận ItemDAO qua constructor thay vì tự new.
 * → Tuân thủ Dependency Inversion Principle (DIP).
 * → Có thể mock ItemDAO trong unit test.
 */
public class ItemService {

    private final ItemDAO itemDAO;

    public ItemService(ItemDAO itemDAO) {
        this.itemDAO = itemDAO;
    }

    /** Thêm sản phẩm mới, liên kết với seller theo sellerId. */
    public void addItem(Map<String, Object> data, int sellerId) throws Exception {
        String name        = data.get("name")        != null ? data.get("name").toString()        : "";
        String description = data.get("description") != null ? data.get("description").toString() : "";
        String category    = data.get("category")    != null ? data.get("category").toString()    : "ART";
        BigDecimal price   = new BigDecimal(data.get("startingPrice").toString());

        Item item = ItemFactory.createItem(category, 0, name, price, description);
        applyOptionalClientFields(item, data);
        itemDAO.insertWithSellerId(item, sellerId);
        System.out.println(">>> [ItemService] Thêm sản phẩm: " + name + " (seller=" + sellerId + ")");
    }

    /** Cập nhật thông tin sản phẩm. Seller phải là chính chủ. */
    public void updateItem(Map<String, Object> data, int sellerId) throws Exception {
        int itemId         = (int) Double.parseDouble(data.get("itemId").toString());
        String name        = data.get("name")        != null ? data.get("name").toString()        : "";
        String description = data.get("description") != null ? data.get("description").toString() : "";
        String category    = data.get("category")    != null ? data.get("category").toString()    : "ART";
        BigDecimal price   = new BigDecimal(data.get("startingPrice").toString());

        Item item = ItemFactory.createItem(category, itemId, name, price, description);
        applyOptionalClientFields(item, data);
        Seller seller = new Seller(sellerId, "", "", "", java.math.BigDecimal.ZERO);
        item.setSeller(seller);
        itemDAO.update(item);
    }

    /** Xóa sản phẩm theo ID. */
    public void deleteItem(int itemId) throws Exception {
        itemDAO.delete(itemId);
    }

    /** Lấy danh sách sản phẩm của một seller. */
    public List<Item> getItemsBySeller(int sellerId) throws Exception {
        return itemDAO.findBySellerId(sellerId);
    }

    /** Lấy sản phẩm theo ID. */
    public Item findById(int itemId) throws Exception {
        return itemDAO.findById(itemId);
    }


    private void applyOptionalClientFields(Item item, Map<String, Object> data) {
        if (item == null || data == null) return;
        if (data.get("imagePath") != null) {
            item.setImagePath(data.get("imagePath").toString());
        }
        if (data.get("bidIncrement") != null) {
            try {
                item.setBidIncrement(new BigDecimal(data.get("bidIncrement").toString()));
            } catch (Exception ignored) { }
        }
    }

    /** Đếm tổng số sản phẩm trong hệ thống (dùng cho admin stats). */
    public int countAll() throws Exception {
        return itemDAO.findAll().size();
    }
}