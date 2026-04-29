package server.dao.interfaces;

import server.dao.core.GenericDAO;
import server.models.items.Item;
import java.util.List;

public interface ItemDAO extends GenericDAO<Item> {
    // Lấy danh sách sản phẩm do một người bán cụ thể đăng lên
    List<Item> findBySellerId(int sellerId) throws Exception;
}
