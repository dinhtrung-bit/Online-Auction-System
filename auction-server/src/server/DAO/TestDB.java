package server.DAO;

import java.sql.Connection;

public class TestDB {
    public static void main(String[] args) {
        System.out.println("Đang thử kết nối tới MySQL...");

        // Gọi hàm getInstance() để lấy kết nối
        Connection conn = DBConnection.getInstance().getConnection();

        if (conn != null) {
            System.out.println("Kết nối Database THÀNH CÔNG!");
        } else {
            System.out.println(" Kết nối THẤT BẠI. Vui lòng kiểm tra lại URL, Username hoặc Password.");
        }
    }
}
