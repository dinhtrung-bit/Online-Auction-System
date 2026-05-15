package server.networks.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import server.models.items.Item;
import server.models.users.User;
import server.networks.dto.MessageDTO;
import server.services.ItemService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            Map<String, Object> data = parseJsonPayload(request);
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
        if (err != null) return err;

        try {
            Map<String, Object> data = parseJsonPayload(request);
            itemService.updateItem(data, loggedInUser.getUserId());
            return new MessageDTO("UPDATE_ITEM_SUCCESS", "Cập nhật sản phẩm thành công!");
        } catch (SecurityException e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("UPDATE_ITEM_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleDeleteItem(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireSeller(loggedInUser);
        if (err != null) return err;

        try {
            int itemId = parseItemId(request.getPayload());

            // Nên dùng bản có check seller trong ItemService
            itemService.deleteItem(itemId, loggedInUser.getUserId());

            return new MessageDTO("DELETE_ITEM_SUCCESS", "Xóa sản phẩm thành công!");
        } catch (SecurityException e) {
            return new MessageDTO("DELETE_ITEM_FAILED", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new MessageDTO("DELETE_ITEM_FAILED", e.getMessage());
        } catch (Exception e) {
            return new MessageDTO("DELETE_ITEM_FAILED", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    public MessageDTO handleGetMyItems(MessageDTO request, User loggedInUser) {
        MessageDTO err = requireLogin(loggedInUser);
        if (err != null) return err;

        try {
            List<Item> items = itemService.getItemsBySeller(loggedInUser.getUserId());

            List<Map<String, Object>> result = items.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemId", i.getItemId());
                m.put("name", i.getName());
                m.put("description", i.getDescription());
                m.put("category", i.getCategoryInfo());
                m.put("startingPrice", i.getStartingPrice());
                m.put("imagePath", i.getImagePath());
                m.put("bidIncrement", i.getBidIncrement());
                return m;
            }).collect(Collectors.toList());

            return new MessageDTO("MY_ITEMS", gson.toJson(result));
        } catch (Exception e) {
            return new MessageDTO("ERROR", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    private MessageDTO requireLogin(User loggedInUser) {
        if (loggedInUser == null) {
            return new MessageDTO("ERROR", "Chưa đăng nhập.");
        }
        return null;
    }

    private MessageDTO requireSeller(User loggedInUser) {
        MessageDTO loginError = requireLogin(loggedInUser);
        if (loginError != null) return loginError;

        if (!loggedInUser.canSell()) {
            return new MessageDTO("ERROR", "Chỉ Seller mới được thực hiện hành động này!");
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonPayload(MessageDTO request) {
        if (request == null || request.getPayload() == null || request.getPayload().trim().isEmpty()) {
            throw new IllegalArgumentException("Payload không được để trống.");
        }

        try {
            Map<String, Object> data = gson.fromJson(request.getPayload(), Map.class);
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("Payload JSON không hợp lệ.");
            }
            return data;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Payload phải là JSON hợp lệ.");
        }
    }

    private int parseItemId(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu itemId.");
        }

        String raw = payload.trim();

        try {
            if (raw.startsWith("{")) {
                Map<String, Object> data = gson.fromJson(raw, Map.class);
                Object itemId = data != null ? data.get("itemId") : null;
                if (itemId == null) {
                    throw new IllegalArgumentException("Thiếu itemId.");
                }
                return toInt(itemId);
            }

            return (int) Double.parseDouble(raw);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Payload xóa sản phẩm không hợp lệ.");
        }
    }

    private int toInt(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Giá trị số không được null.");
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        return (int) Double.parseDouble(value.toString());
    }
}