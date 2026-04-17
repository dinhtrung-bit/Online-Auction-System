package server.controllers;

import java.util.Date;

class AuctionProduct {
    private String name;
    private String description;
    private double startingPrice;
    private Date startTime;
    private Date endTime;
    private String status; // OPEN, RUNNING, FINISHED, PAID, CANCELED

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public AuctionProduct(String name, String description, double startingPrice, Date startTime, Date endTime) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "OPEN";
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getEndTime() {
        return endTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public String getDescription() {
        return description;
    }
}
