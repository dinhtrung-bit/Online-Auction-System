package com.auctions.server.models;

public class Art extends Item {
    private String artist;

    public Art(String itemId, String name, double startingPrice, String artist) {
        super(itemId, name, startingPrice);
        this.artist = artist;
    }

    @Override
    public String getCategoryInfo() {
        return "Nghệ thuật - Tác giả: " + artist;
    }
}