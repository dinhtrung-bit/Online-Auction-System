package server.services;

import server.dao.interfaces.ItemDAO;
import server.models.items.Item;
import server.models.items.ItemFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ItemService {

    private final ItemDAO itemDAO;

    public ItemService(ItemDAO itemDAO) {
        this.itemDAO = itemDAO;
    }

    public void addItem(Map<String, Object> data, int sellerId) throws Exception {
        String name = getString(data, "name", "");
        String description = getString(data, "description", "");
        String category = getString(data, "category", "ART");
        BigDecimal price = getMoney(data, "startingPrice");

        Item item = ItemFactory.createItem(category, 0, name, price, description);
        applyOptionalClientFields(item, data);

        itemDAO.insertWithSellerId(item, sellerId);

        System.out.println(">>> [ItemService] Thêm sản phẩm: " + name + " (seller=" + sellerId + ")");
    }

    public void updateItem(Map<String, Object> data, int sellerId) throws Exception {
        int itemId = getInt(data, "itemId");

        Item oldItem = itemDAO.findById(itemId);
        if (oldItem == null) {
            throw new IllegalArgumentException("Sản phẩm không tồn tại.");
        }

        if (oldItem.getSeller() == null || oldItem.getSeller().getUserId() != sellerId) {
            throw new SecurityException("Bạn không có quyền sửa sản phẩm này.");
        }

        String name = getString(data, "name", oldItem.getName());
        String description = getString(data, "description", oldItem.getDescription());
        String category = getString(data, "category", oldItem.getCategoryInfo());

        BigDecimal price = data.get("startingPrice") != null
                ? getMoney(data, "startingPrice")
                : oldItem.getStartingPrice();

        Item updatedItem = ItemFactory.createItem(category, itemId, name, price, description);
        updatedItem.setSeller(oldItem.getSeller());

        updatedItem.setImagePath(oldItem.getImagePath());
        updatedItem.setBidIncrement(oldItem.getBidIncrement());

        applyOptionalClientFields(updatedItem, data);

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

    private void applyOptionalClientFields(Item item, Map<String, Object> data) {
        if (item == null || data == null) return;

        if (data.get("imagePath") != null) {
            item.setImagePath(data.get("imagePath").toString());
        }

        if (data.get("bidIncrement") != null) {
            try {
                item.setBidIncrement(new BigDecimal(data.get("bidIncrement").toString()));
            } catch (Exception ignored) {
            }
        }
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;

        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private int getInt(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return (int) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số nguyên.");
        }
    }

    private BigDecimal getMoney(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + key);
        }

        try {
            BigDecimal money = new BigDecimal(value.toString());

            if (money.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Giá tiền phải lớn hơn 0.");
            }

            return money;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số hợp lệ.");
        }
    }
}