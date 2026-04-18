package client.models;

public class Auction {
    private int id;
    private String itemName;
    private double currentPrice;
    private String currentWinner;
    private String status;

    public Auction(int id, String itemName, double currentPrice, String currentWinner, String status) {
        this.id = id;
        this.itemName = itemName;
        this.currentPrice = currentPrice;
        this.currentWinner = currentWinner;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getItemName() {
        return itemName;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public String getCurrentWinner() {
        return currentWinner;
    }

    public String getStatus() {
        return status;
    }
}