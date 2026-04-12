package com.auctions.server.DAO;

import com.auctions.server.models.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ItemDAOimpl implements ItemDAO {

    @Override
    public void insert(Item item) throws Exception {
        // Giả sử bảng items có 4 cột cần thêm, item_id tự động tăng
        String sql = "INSERT INTO items (seller_id, name, description, category) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, item.getItemId());
            pstmt.setString(2, item.getName());
            pstmt.setString(3, item.getDescription());
            pstmt.setString(4,item.getCategory());
            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(Item item) throws Exception {
        // Cập nhật thông tin sản phẩm dựa trên item_id
        String sql = "UPDATE items SET seller_id = ?, name = ?, description = ?, category = ? WHERE item_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, item.getItemId()); // Điều kiện WHERE
            pstmt.setString(2, item.getName());
            pstmt.setString(3, item.getDescription());
            pstmt.setString(4, item.getCategory());

            pstmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws Exception {
        // Xóa sản phẩm theo item_id
        String sql = "DELETE FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<Item> findBySellerId(int sellerId) throws Exception {
        return List.of();
    }

    @Override
    public List<Item> findAll() throws Exception {
        return List.of();
    }
}