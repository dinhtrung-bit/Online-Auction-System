package com.auctions.server.dao;

import java.sql.*;

public class DatabaseDAO {
    // Đường dẫn tạo file data.db ngay trong thư mục project
    private static final String URL = "jdbc:sqlite:data.db";

    // 1. Hàm khởi tạo Database (Tạo các bảng nếu chưa có)
    public static void initDatabase() {
        String sqlUsers = "CREATE TABLE IF NOT EXISTS Users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE, " +
                "role TEXT, " +
                "balance REAL)";

        String sqlItems = "CREATE TABLE IF NOT EXISTS Items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "description TEXT, " +
                "start_price REAL, " +
                "status TEXT, " +
                "seller_name TEXT)";

        String sqlBids = "CREATE TABLE IF NOT EXISTS Bids (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "item_name TEXT, " +
                "bidder_name TEXT, " +
                "bid_amount REAL, " +
                "bid_time DATETIME DEFAULT CURRENT_TIMESTAMP)";

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute(sqlUsers);
            stmt.execute(sqlItems);
            stmt.execute(sqlBids);
            System.out.println("[DB] Đã khởi tạo cấu trúc CSDL SQLite thành công!");

        } catch (SQLException e) {
            System.out.println("[DB Lỗi] Không thể khởi tạo Database: " + e.getMessage());
        }
    }

    // 2. Hàm lưu Sản phẩm mới (Dành cho Seller)
    public static void saveNewItem(String name, String desc, double price, String sellerName) {
        String sql = "INSERT INTO Items(name, description, start_price, status, seller_name) VALUES(?,?,?,'OPEN',?)";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setString(2, desc);
            pstmt.setDouble(3, price);
            pstmt.setString(4, sellerName);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println("[DB Lỗi] Lưu sản phẩm thất bại: " + e.getMessage());
        }
    }

    // 3. Hàm lưu Lịch sử đặt giá (Dành cho Bidder)
    public static void saveBidHistory(String itemName, String bidderName, double amount) {
        String sql = "INSERT INTO Bids(item_name, bidder_name, bid_amount) VALUES(?,?,?)";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemName);
            pstmt.setString(2, bidderName);
            pstmt.setDouble(3, amount);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println("[DB Lỗi] Lưu lịch sử đấu giá thất bại: " + e.getMessage());
        }
    }
}