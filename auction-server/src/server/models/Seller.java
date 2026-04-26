package server.models;

public class Seller extends User {
    public Seller(int userId, String username) {
        super(userId, username, "SELLER");
    }

    @Override
    public void displayDashboard() {
        System.out.println("Hiển thị chức năng Đăng sản phẩm mới cho Seller.");
    }
}