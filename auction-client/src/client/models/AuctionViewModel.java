package client.models;

<<<<<<<< HEAD:auction-client/src/client/models/Auction.java
public class Auction {
========
public class AuctionViewModel {
>>>>>>>> 975c8bcf6c07664b14552ffc5de027defdcb0a70:auction-client/src/client/models/AuctionViewModel.java
    private int id;
    private String itemName;
    private double currentPrice;
    private String currentWinner;
    private String status;

<<<<<<<< HEAD:auction-client/src/client/models/Auction.java
    public Auction(int id, String itemName, double currentPrice, String currentWinner, String status) {
========
    public AuctionViewModel(int id, String itemName, double currentPrice, String currentWinner, String status) {
>>>>>>>> 975c8bcf6c07664b14552ffc5de027defdcb0a70:auction-client/src/client/models/AuctionViewModel.java
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
