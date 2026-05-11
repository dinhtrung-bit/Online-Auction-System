package client.models.item;

public class Art extends Item {

    public Art(String itemId, String name, double startingPrice, String artist) {
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