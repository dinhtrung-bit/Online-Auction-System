package com.auctions.server.models;

public class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String itemId, String name, double startingPrice, int warranty,String description) {
        super(itemId, name, startingPrice,description);
        this.warrantyMonths = warranty;
    }

    @Override
    public String getCategory() {
        return "Điện tử - Bảo hành: " + warrantyMonths + " tháng";
    }
}