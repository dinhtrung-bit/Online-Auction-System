<<<<<<< HEAD
package server.models;
=======
package src.server.models;
>>>>>>> thắng

public class Art extends Item {
    private String artist;

<<<<<<< HEAD
    public Art(String itemId, String name, double startingPrice, String artist) {
        super(itemId, name, startingPrice);
=======
    public Art(String itemId, String name, double startingPrice, String artist,String description) {
        super(itemId, name, startingPrice,description);
>>>>>>> thắng
        this.artist = artist;
    }

    @Override
<<<<<<< HEAD
    public String getCategoryInfo() {
=======
    public String getCategory() {
>>>>>>> thắng
        return "Nghệ thuật - Tác giả: " + artist;
    }
}