package server.models;

public class Admin extends User {

    public Admin(String userId, String username) {
        super(userId, username, "ADMIN");
    }
    @Override
    public void displayDashboard() {
        System.out.println("Đang hiển thị màn hình quản trị hệ thống");
    }
}
