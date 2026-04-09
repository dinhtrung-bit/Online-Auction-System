package com.auctions.server.models;

public class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String itemId, String name, double startingPrice, int warranty) {
        super(itemId, name, startingPrice);
        this.warrantyMonths = warranty;
    }

    @Override
    public String getCategoryInfo() {
        return "Điện tử - Bảo hành: " + warrantyMonths + " tháng";
    }
}