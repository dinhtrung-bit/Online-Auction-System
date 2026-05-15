package server.dao.impl;

import server.dao.core.DBConnection;
import server.dao.interfaces.ItemDAO;
import server.models.items.Item;
import server.models.items.ItemFactory;
import server.models.users.Seller;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAOImpl implements ItemDAO {

    private volatile boolean optionalColumnsChecked = false;
    private volatile boolean hasImagePathColumn = false;
    private volatile boolean hasBidIncrementColumn = false;

    @Override
    public void insert(Item item) throws Exception {
        insertWithSellerId(item, item.getSeller().getUserId());
    }

    @Override
    public int insertWithSellerId(Item item, int sellerId) throws Exception {
        try (Connection conn = DBConnection.getInstance()) {
            ensureOptionalColumns(conn);

            String sql;
            if (hasImagePathColumn && hasBidIncrementColumn) {
                sql = "INSERT INTO items (seller_id, name, description, CategoryInfo, startingPrice, imagePath, bidIncrement) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
            } else if (hasImagePathColumn) {
                sql = "INSERT INTO items (seller_id, name, description, CategoryInfo, startingPrice, imagePath) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
            } else if (hasBidIncrementColumn) {
                sql = "INSERT INTO items (seller_id, name, description, CategoryInfo, startingPrice, bidIncrement) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO items (seller_id, name, description, CategoryInfo, startingPrice) " +
                        "VALUES (?, ?, ?, ?, ?)";
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                pstmt.setInt(i++, sellerId);
                pstmt.setString(i++, item.getName());
                pstmt.setString(i++, item.getDescription());
                pstmt.setString(i++, item.getCategoryInfo());
                pstmt.setBigDecimal(i++, item.getStartingPrice());

                if (hasImagePathColumn) {
                    pstmt.setString(i++, item.getImagePath());
                }

                if (hasBidIncrementColumn) {
                    pstmt.setBigDecimal(i++, item.getBidIncrement());
                }

                pstmt.executeUpdate();

                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        }

        return -1;
    }

    @Override
    public void update(Item item) throws Exception {
        if (item == null) {
            throw new IllegalArgumentException("Item không được null.");
        }

        if (item.getSeller() == null) {
            throw new IllegalArgumentException("Item phải có seller.");
        }

        try (Connection conn = DBConnection.getInstance()) {
            ensureOptionalColumns(conn);

            String sql;
            if (hasImagePathColumn && hasBidIncrementColumn) {
                sql = "UPDATE items SET seller_id = ?, name = ?, description = ?, CategoryInfo = ?, startingPrice = ?, imagePath = ?, bidIncrement = ? WHERE item_id = ?";
            } else if (hasImagePathColumn) {
                sql = "UPDATE items SET seller_id = ?, name = ?, description = ?, CategoryInfo = ?, startingPrice = ?, imagePath = ? WHERE item_id = ?";
            } else if (hasBidIncrementColumn) {
                sql = "UPDATE items SET seller_id = ?, name = ?, description = ?, CategoryInfo = ?, startingPrice = ?, bidIncrement = ? WHERE item_id = ?";
            } else {
                sql = "UPDATE items SET seller_id = ?, name = ?, description = ?, CategoryInfo = ?, startingPrice = ? WHERE item_id = ?";
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int i = 1;
                pstmt.setInt(i++, item.getSeller().getUserId());
                pstmt.setString(i++, item.getName());
                pstmt.setString(i++, item.getDescription());
                pstmt.setString(i++, item.getCategoryInfo());
                pstmt.setBigDecimal(i++, item.getStartingPrice());

                if (hasImagePathColumn) {
                    pstmt.setString(i++, item.getImagePath());
                }

                if (hasBidIncrementColumn) {
                    pstmt.setBigDecimal(i++, item.getBidIncrement());
                }

                pstmt.setInt(i, item.getItemId());

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new IllegalArgumentException("Không tìm thấy sản phẩm để cập nhật.");
                }
            }
        }
    }

    @Override
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.getInstance();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalArgumentException("Không tìm thấy sản phẩm để xóa.");
            }
        }
    }

    @Override
    public List<Item> findBySellerId(int sellerId) throws Exception {
        List<Item> itemList = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ?";

        try (Connection conn = DBConnection.getInstance()) {
            ensureOptionalColumns(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, sellerId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        itemList.add(mapResultSetToItem(rs));
                    }
                }
            }
        }

        return itemList;
    }

    @Override
    public List<Item> findAll() throws Exception {
        List<Item> itemList = new ArrayList<>();
        String sql = "SELECT * FROM items";

        try (Connection conn = DBConnection.getInstance()) {
            ensureOptionalColumns(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Item item = mapResultSetToItem(rs);
                    if (item != null) {
                        itemList.add(item);
                    }
                }
            }
        }

        return itemList;
    }

    @Override
    public Item findById(int id) throws Exception {
        String sql = "SELECT * FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.getInstance()) {
            ensureOptionalColumns(conn);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToItem(rs);
                    }
                }
            }
        }

        return null;
    }

    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        int itemId = rs.getInt("item_id");
        int sellerId = rs.getInt("seller_id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        String categoryInfo = rs.getString("CategoryInfo");
        BigDecimal startingPrice = rs.getBigDecimal("startingPrice");

        Item item = ItemFactory.createItem(categoryInfo, itemId, name, startingPrice, description);

        Seller seller = new Seller(sellerId, "", "", "", BigDecimal.ZERO);
        item.setSeller(seller);

        if (hasColumn(rs, "imagePath")) {
            item.setImagePath(rs.getString("imagePath"));
        }

        if (hasColumn(rs, "bidIncrement")) {
            item.setBidIncrement(rs.getBigDecimal("bidIncrement"));
        }

        return item;
    }

    private void ensureOptionalColumns(Connection conn) {
        if (optionalColumnsChecked) return;

        synchronized (this) {
            if (optionalColumnsChecked) return;

            try {
                hasImagePathColumn = columnExists(conn, "items", "imagePath");
                hasBidIncrementColumn = columnExists(conn, "items", "bidIncrement");

                if (!hasImagePathColumn) {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate("ALTER TABLE items ADD COLUMN imagePath VARCHAR(1000)");
                    } catch (Exception ignored) {
                    }

                    hasImagePathColumn = columnExists(conn, "items", "imagePath");
                }

                if (!hasBidIncrementColumn) {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate("ALTER TABLE items ADD COLUMN bidIncrement DECIMAL(15,2) DEFAULT 0");
                    } catch (Exception ignored) {
                    }

                    hasBidIncrementColumn = columnExists(conn, "items", "bidIncrement");
                }
            } catch (Exception ignored) {
            }

            optionalColumnsChecked = true;
        }
    }

    private boolean columnExists(Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {
        }

        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table.toUpperCase(), column)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {
        }

        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column.toUpperCase())) {
            return rs.next();
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean hasColumn(ResultSet rs, String column) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();

        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnName(i))) {
                return true;
            }
        }

        return false;
    }
}