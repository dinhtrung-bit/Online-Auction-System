package client.models;

public class Seller extends User {

    public Seller(String userId, String username) {
        super(userId, username, "SELLER");
    }

    @Override
    public String getDashboardType() {
        return "Màn hình Quản lý Phiên đấu giá (Dành cho Người bán)";
    }
}