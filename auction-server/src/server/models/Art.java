package src.server.models;

public class Art extends Item {
    private String artist;

    public Art(String itemId, String name, double startingPrice, String artist,String description) {
        super(itemId, name, startingPrice,description);
        this.artist = artist;
    }

    @Override
    public String getCategory() {
        return "Nghệ thuật - Tác giả: " + artist;
    }
}