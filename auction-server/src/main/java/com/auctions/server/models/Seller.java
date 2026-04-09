package com.auctions.server.models;

class Seller extends User {
    public Seller(String userId, String username) {
        super(userId, username, "SELLER");
    }

    @Override
    public void displayDashboard() {
        System.out.println("Hiển thị chức năng Đăng sản phẩm mới cho Seller.");
    }
}