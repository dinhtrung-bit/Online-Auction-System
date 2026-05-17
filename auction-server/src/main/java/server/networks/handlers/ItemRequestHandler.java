package server.networks.handlers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import server.models.items.Item;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.ItemService;

/** Xử lý các request CRUD sản phẩm: ADD_ITEM, UPDATE_ITEM, DELETE_ITEM, GET_MY_ITEMS. */
public class ItemRequestHandler {

    private final ItemService itemService;
    private final Gson gson = new Gson();

    public ItemRequestHandler(ItemService itemService) {
        this.itemService = itemService;
    }

    // ─── Seller actions ──────────────────────────────────────────────────────

    public MessageDTO handleAddItem(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireSeller(loggedInUser);
        if (err != null) {
            return err;
        }
        try {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);
            itemService.addItem(data, loggedInUser.getUserId());
            return new MessageDTO("ADD_ITEM_SUCCESS", "Thêm sản phẩm thành công!");
        } catch (IllegalArgumentException e) {
            return new MessageDTO("ADD_ITEM_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("ADD_ITEM_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleUpdateItem(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireSeller(loggedInUser);
        if (err != null) {
            return err;
        }
        try {
            Map<String, Object> data = PayloadParser.parseJsonPayload(request);
            itemService.updateItem(data, loggedInUser.getUserId());
            return new MessageDTO("UPDATE_ITEM_SUCCESS", "Cập nhật sản phẩm thành công!");
        } catch (SecurityException | IllegalArgumentException e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleDeleteItem(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireSeller(loggedInUser);
        if (err != null) {
            return err;
        }
        try {
            int itemId = (int) PayloadParser.parseIdPayload(request.getPayload(), "itemId");
            itemService.deleteItem(itemId, loggedInUser.getUserId());
            return new MessageDTO("DELETE_ITEM_SUCCESS", "Xóa sản phẩm thành công!");
        } catch (SecurityException | IllegalArgumentException e) {
            return new MessageDTO("DELETE_ITEM_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DELETE_ITEM_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyItems(MessageDTO request, User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập.");
        }
        try {
            List<Item> items = itemService.getItemsBySeller(loggedInUser.getUserId());

            List<Map<String, Object>> result = items.stream()
                    .map(this::toItemMap)
                    .collect(Collectors.toList());

            return new MessageDTO("MY_ITEMS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Map<String, Object> toItemMap(Item item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId",        item.getItemId());
        m.put("name",          item.getName());
        m.put("description",   item.getDescription());
        m.put("category",      item.getCategoryInfo());
        m.put("startingPrice", item.getStartingPrice());
        m.put("imagePath",     item.getImagePath());
        m.put("bidIncrement",  item.getBidIncrement());
        return m;
    }

    private MessageDTO requireSeller(User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập.");
        }
        if (!loggedInUser.canSell()) {
            return new MessageDTO("ERROR", "Chỉ Seller mới được thực hiện hành động này!");
        }
        return null;
    }
}