import server.dao.core.DBConnection;

import java.sql.Connection;

public class TestDB {
    public static void main(String[] args) {
        System.out.println("Đang thử kết nối tới MySQL...");
        try {
            Connection conn = DBConnection.getConnection();
            if (conn != null) {
                System.out.println("Kết nối Database THÀNH CÔNG!");
            } else {
                System.out.println("Kết nối THẤT BẠI.");
            }
        } catch (Exception e) {
            System.out.println("Kết nối THẤT BẠI: " + e.getMessage());
        }
    }
}