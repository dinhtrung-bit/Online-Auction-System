package client.models;

// Nhớ có chữ public ở đây nha
public class Electronics extends Item {
    private int warrantyMonths;

    public Electronics(String itemId, String name, double startingPrice, int warrantyMonths) {
        super(itemId, name, startingPrice);
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public String getDetails() {
        return "Hàng điện tử - Bảo hành: " + warrantyMonths + " tháng";
    }
}
