package server.models.items;

public class Electronics extends Item {

    public Electronics(int itemId, String name, double startingPrice,String description) {
        super(itemId, name, startingPrice,description);
    }

    @Override
    public String getCategoryInfo() {
        return "ELECTRONIC";
    }
}