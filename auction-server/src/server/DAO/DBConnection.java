package src.server.DAO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static DBConnection instance;
    private Connection connection;
    private static final String URL = "jdbc:mysql://localhost:3306/daugia";
    private static final String USER = "root";
    private static final String PASSWORD = "thang2007";

    private DBConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Kết nối cơ sở dữ liệu thành công");
        }
        catch(ClassNotFoundException | SQLException e){
            e.printStackTrace();
            throw new RuntimeException("Lỗi kết nối cơ sở dữ liệu");
        }
    }
    public static DBConnection getInstance() {
        try {
            if (instance == null || instance.getConnection().isClosed()) {
                instance = new DBConnection();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }
}
