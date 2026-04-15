package src.server.models;

public class Bidder extends User {
    private double balance;

    public Bidder(String userId, String username, double balance) {
        super(userId, username, "BIDDER");
        this.balance = balance;
    }

    public double getBalance() {
        return balance;
    }

    @Override
    public void displayDashboard() {
        System.out.println("Hiển thị danh sách sản phẩm đang đấu giá cho Bidder.");
    }
}