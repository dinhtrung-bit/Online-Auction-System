package server.models.items;

import java.math.BigDecimal;

public class Art extends Item {

    public Art(int itemId, String name, BigDecimal startingPrice, String description) {
        super(itemId, name, startingPrice, description);
    }

    @Override
    public String getCategoryInfo() {
        return "ART" ;
    }
}