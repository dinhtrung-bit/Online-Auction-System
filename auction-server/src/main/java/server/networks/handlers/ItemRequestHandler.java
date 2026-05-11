package server.networks.handlers;

import com.google.gson.Gson;
import server.models.items.Item;
import server.models.users.Seller;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.ItemService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ItemRequestHandler — xử lý ADD_ITEM, UPDATE_ITEM, DELETE_ITEM, GET_MY_ITEMS.
 *
 * Chỉ biết đến ItemService — không gọi bất kỳ DAO nào trực tiếp.
 * Tuân thủ: Single Responsibility Principle + Layered Architecture.
 */
public class ItemRequestHandler {

    private final ItemService itemService;
    private final Gson gson = new Gson();

    public ItemRequestHandler(ItemService itemService) {
        this.itemService = itemService;
    }

    public MessageDTO handleAddItem(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireSeller(loggedInUser);
        if (err != null) return err;
        try {
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);
            itemService.addItem(data, loggedInUser.getUserId());
            return new MessageDTO("ADD_ITEM_SUCCESS", "Thêm sản phẩm thành công!");
        } catch (Exception e) {
            System.err.println(">>> [ADD_ITEM] Lỗi: " + e.getMessage());
            return new MessageDTO("ADD_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleUpdateItem(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireSeller(loggedInUser);
        if (err != null) return err;
        try {
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);
            itemService.updateItem(data, loggedInUser.getUserId());
            return new MessageDTO("UPDATE_ITEM_SUCCESS", "Cập nhật thành công!");
        } catch (Exception e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleDeleteItem(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireSeller(loggedInUser);
        if (err != null) return err;
        try {
            int itemId = (int) Double.parseDouble(request.getPayload().trim());
            itemService.deleteItem(itemId);
            return new MessageDTO("DELETE_ITEM_SUCCESS", "Xóa sản phẩm thành công!");
        } catch (Exception e) {
            return new MessageDTO("DELETE_ITEM_FAILED", "Lỗi: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyItems(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        try {
            List<Item> items = itemService.getItemsBySeller(loggedInUser.getUserId());
            List<Map<String, Object>> result = items.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemId",        i.getItemId());
                m.put("name",          i.getName());
                m.put("description",   i.getDescription());
                m.put("category",      i.getCategoryInfo());
                m.put("startingPrice", i.getStartingPrice());
                return m;
            }).collect(Collectors.toList());
            return new MessageDTO("MY_ITEMS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi: " + e.getMessage());
        }
    }

    private MessageDTO requireSeller(User loggedInUser) {
        if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
        if (!(loggedInUser instanceof Seller))
            return new MessageDTO("ERROR", "Chỉ Seller mới được thực hiện hành động này!");
        return null;
    }
}