package server.models;

public class Vehicle extends Item{
    private String brand;
    private int year;
    private String engine;
    public Vehicle(int itemId, String name, String description, double startingPrice){
        super(itemId,name, startingPrice,description);
    }

    @Override
    public String getCategoryInfo() {
        return "VEHICLE";
    }
}
