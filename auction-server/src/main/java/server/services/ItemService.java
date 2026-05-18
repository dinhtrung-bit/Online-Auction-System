package server.services;

import server.dao.interfaces.ItemDAO;
import server.models.items.Item;
import server.models.items.ItemFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ItemService {

    private final ItemDAO itemDAO;
    private final ItemDataMapper itemDataMapper;

    public ItemService(ItemDAO itemDAO) {
        this.itemDAO = itemDAO;
        this.itemDataMapper = new ItemDataMapper();
    }

    public void addItem(Map<String, Object> data, int sellerId) throws Exception {
        String name = itemDataMapper.getString(data, "name", "");
        String description = itemDataMapper.getString(data, "description", "");
        String category = itemDataMapper.getString(data, "category", "ART");
        BigDecimal price = itemDataMapper.getMoney(data, "startingPrice");

        Item item = ItemFactory.createItem(category, 0, name, price, description);
        itemDataMapper.applyOptionalClientFields(item, data);

        itemDAO.insertWithSellerId(item, sellerId);

        System.out.println(">>> [ItemService] Thêm sản phẩm: " + name + " (seller=" + sellerId + ")");
    }

    public void updateItem(Map<String, Object> data, int sellerId) throws Exception {
        int itemId = itemDataMapper.getInt(data, "itemId");

        Item oldItem = itemDAO.findById(itemId);
        if (oldItem == null) {
            throw new IllegalArgumentException("Sản phẩm không tồn tại.");
        }

        if (oldItem.getSeller() == null || oldItem.getSeller().getUserId() != sellerId) {
            throw new SecurityException("Bạn không có quyền sửa sản phẩm này.");
        }

        String name = itemDataMapper.getString(data, "name", oldItem.getName());
        String description = itemDataMapper.getString(data, "description", oldItem.getDescription());
        String category = itemDataMapper.getString(data, "category", oldItem.getCategoryInfo());

        BigDecimal price = data.get("startingPrice") != null
                ? itemDataMapper.getMoney(data, "startingPrice")
                : oldItem.getStartingPrice();

        Item updatedItem = ItemFactory.createItem(category, itemId, name, price, description);
        updatedItem.setSeller(oldItem.getSeller());

        updatedItem.setImagePath(oldItem.getImagePath());
        updatedItem.setBidIncrement(oldItem.getBidIncrement());

        itemDataMapper.applyOptionalClientFields(updatedItem, data);

        itemDAO.update(updatedItem);

        System.out.println(">>> [ItemService] Cập nhật sản phẩm #" + itemId + " bởi seller=" + sellerId);
    }

    public void deleteItem(int itemId) throws Exception {
        itemDAO.delete(itemId);
    }

    public void deleteItem(int itemId, int sellerId) throws Exception {
        Item oldItem = itemDAO.findById(itemId);

        if (oldItem == null) {
            throw new IllegalArgumentException("Sản phẩm không tồn tại.");
        }

        if (oldItem.getSeller() == null || oldItem.getSeller().getUserId() != sellerId) {
            throw new SecurityException("Bạn không có quyền xóa sản phẩm này.");
        }

        itemDAO.delete(itemId);

        System.out.println(">>> [ItemService] Xóa sản phẩm #" + itemId + " bởi seller=" + sellerId);
    }

    public List<Item> getItemsBySeller(int sellerId) throws Exception {
        return itemDAO.findBySellerId(sellerId);
    }

    public Item findById(int itemId) throws Exception {
        return itemDAO.findById(itemId);
    }

    public int countAll() throws Exception {
        return itemDAO.findAll().size();
    }
}
