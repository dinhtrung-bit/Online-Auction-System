package server.models.items;

import java.math.BigDecimal;

public class Electronics extends Item {

    public Electronics(int itemId, String name, BigDecimal startingPrice, String description) {
        super(itemId, name, startingPrice,description);
    }

    @Override
    public String getCategoryInfo() {
        return "ELECTRONIC";
    }
}