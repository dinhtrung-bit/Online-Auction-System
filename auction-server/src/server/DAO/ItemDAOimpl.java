package server.DAO;

import server.models.Item;
import server.models.ItemFactory;
import server.models.Seller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ItemDAOimpl implements ItemDAO {

    @Override
    public void insert(Item item) throws Exception {
        // items có 4 cột cần thêm
        String sql = "INSERT INTO items (name, description, CategoryInfo,startingPrice) VALUES ( ?, ?, ?,?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

          // pstmt.setInt(1, item.getseller().getUserId());
            pstmt.setString(1, item.getName());
           pstmt.setString(2, item.getDescription());
            pstmt.setString(3,item.getCategoryInfo());
            pstmt.setDouble(4,item.getStartingPrice());
            pstmt.executeUpdate();
        }
    }

    @Override
    public void update(Item item) throws Exception {
        // Cập nhật thông tin sản phẩm dựa trên item_id
        String sql = "UPDATE items SET name = ?, description = ?, category = ? WHERE item_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, item.getName());
           pstmt.setString(2, item.getDescription());
            pstmt.setString(3, item.getCategoryInfo());
            pstmt.setInt(4, item.getItemId());// Điều kiện WHERE

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
        List<Item> itemlist = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sellerId);
            try (ResultSet rs = pstmt.executeQuery()) {

                // Duyệt qua từng dòng dữ liệu (từng sản phẩm) lấy được từ Database
                while (rs.next()) {
                    int itemId = rs.getInt("item_id");
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    String CategoryInfo=rs.getString("CategoryInfo");
                    double startingPrice=rs.getDouble("startingPrice");
                    Item item=ItemFactory.createItem(CategoryInfo,itemId,name,startingPrice,description);
                    itemlist.add(item);
                }
            }
        }
        return itemlist;
    }
    @Override
    public List<Item> findAll() throws Exception {
        List<Item> itemList = new ArrayList<>();
        String sql = "SELECT item_id, name, startingPrice, description, categoryinfo FROM items";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                int itemId = rs.getInt("item_id");
                String name = rs.getString("name");
                double startingPrice = rs.getDouble("startingPrice");
                String description = rs.getString("description");
                String category = rs.getString("CategoryInfo");
                Item item = ItemFactory.createItem(category,itemId, name, startingPrice,description);

                if (item != null) {
                    itemList.add(item);
                }
            }
        }
        return itemList;
    }
}