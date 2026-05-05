package client.models;

// Nhớ có chữ public ở đây nha
public class Art extends Item {
    private String artist;

    public Art(String itemId, String name, double startingPrice, String artist) {
        super(itemId, name, startingPrice);
        this.artist = artist;
    }

    @Override
    public String getDetails() {
        return "Tác phẩm nghệ thuật bởi: " + artist;
    }
}