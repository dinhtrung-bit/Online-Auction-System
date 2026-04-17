<<<<<<< HEAD
package server.models;
=======
package src.server.models;
>>>>>>> thắng

public class Electronics extends Item {
    private int warrantyMonths;

<<<<<<< HEAD
    public Electronics(String itemId, String name, double startingPrice, int warranty) {
        super(itemId, name, startingPrice);
=======
    public Electronics(String itemId, String name, double startingPrice, int warranty,String description) {
        super(itemId, name, startingPrice,description);
>>>>>>> thắng
        this.warrantyMonths = warranty;
    }

    @Override
<<<<<<< HEAD
    public String getCategoryInfo() {
=======
    public String getCategory() {
>>>>>>> thắng
        return "Điện tử - Bảo hành: " + warrantyMonths + " tháng";
    }
}