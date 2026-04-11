package com.auctions.server.DAO;

import com.auctions.server.models.Item;
import java.util.List;

public interface ItemDAO extends GenericDAO<Item> {
    // Lấy danh sách sản phẩm do một người bán cụ thể đăng lên
    List<Item> findBySellerId(int sellerId) throws Exception;
}
