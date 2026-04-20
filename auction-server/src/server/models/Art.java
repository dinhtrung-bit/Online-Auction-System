package server.models;

public class Art extends Item {

    public Art(int itemId, String name, double startingPrice,String description) {
        super(itemId, name, startingPrice,description);
    }

    @Override
    public String getCategoryInfo() {
        return "Nghệ thuật" ;
    }
}