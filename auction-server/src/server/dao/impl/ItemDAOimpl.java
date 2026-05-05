package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.ItemDAO;
import server.models.items.Item;
import server.models.items.ItemFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ItemDAOimpl implements ItemDAO {

    @Override
    public void insert(Item item) throws Exception {
        String sql = "INSERT INTO items (seller_id, name, description, CategoryInfo, startingPrice) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, item.getSeller().getUserId());
            pstmt.setString(2, item.getName());
            pstmt.setString(3, item.getDescription());
            pstmt.setString(4, item.getCategoryInfo());
            pstmt.setBigDecimal(5, item.getStartingPrice());
            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(Item item) throws Exception {
        String sql = "UPDATE items SET seller_id = ?, name = ?, description = ?, CategoryInfo = ?, startingPrice = ? WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, item.getSeller().getUserId());
            pstmt.setString(2, item.getName());
            pstmt.setString(3, item.getDescription());
            pstmt.setString(4, item.getCategoryInfo());
            pstmt.setBigDecimal(5, item.getStartingPrice());
            pstmt.setInt(6, item.getItemId());
            pstmt.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<Item> findBySellerId(int sellerId) throws Exception {
        List<Item> itemlist = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, sellerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    itemlist.add(mapResultSetToItem(rs));
                }
            }
        }
        return itemlist;
    }

    @Override
    public List<Item> findAll() throws Exception {
        List<Item> itemList = new ArrayList<>();
        String sql = "SELECT item_id, name, startingPrice, description, CategoryInfo FROM items";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Item item = mapResultSetToItem(rs);
                if (item != null) itemList.add(item);
            }
        }
        return itemList;
    }

    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        int itemId = rs.getInt("item_id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        String categoryInfo = rs.getString("CategoryInfo");
        BigDecimal startingPrice = rs.getBigDecimal("startingPrice");

        return ItemFactory.createItem(categoryInfo, itemId, name, startingPrice, description);
    }

    public void insertWithSellerId(Item item, int sellerId) throws Exception {
        String sql = "INSERT INTO items (seller_id, name, description, CategoryInfo, startingPrice) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, sellerId);
            pstmt.setString(2, item.getName());
            pstmt.setString(3, item.getDescription());
            pstmt.setString(4, item.getCategoryInfo());
            pstmt.setBigDecimal(5, item.getStartingPrice());
            pstmt.executeUpdate();
        }
    }

    @Override
    public Item findById(int id) throws Exception {
        String sql = "SELECT * FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToItem(rs);
            }
        }
        return null;
    }
}