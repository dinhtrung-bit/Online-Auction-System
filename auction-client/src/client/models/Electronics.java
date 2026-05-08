package client.models;

public class Electronics extends Item {

    public Electronics(String itemId, String name, double startingPrice, int warrantyMonths) {
        super(itemId, name, startingPrice);
    }

    @Override
    public String getDetails() {
        String desc = getDescription();

        if (desc == null || desc.isBlank()) {
            return "Chưa có mô tả";
        }

        return desc;
    }
}