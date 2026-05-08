package server.dao.core;

import server.exceptions.DatabaseConnectionException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static DBConnection instance;
    private Connection connection;

    private static final String URL =
            "jdbc:mysql://mysql-24b3aff1-vnu-8ca6.k.aivencloud.com:15190/daugia?sslMode=REQUIRED";

    private static final String USER = "avnadmin";
    private static final String PASSWORD = "";

    private DBConnection() {
        connect();
    }

    private void connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Kết nối cơ sở dữ liệu thành công");
        } catch (ClassNotFoundException | SQLException e) {
            throw new DatabaseConnectionException("Lỗi kết nối cơ sở dữ liệu", e);
        }
    }

    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
            return connection;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Lỗi kiểm tra kết nối cơ sở dữ liệu", e);
        }
    }
}