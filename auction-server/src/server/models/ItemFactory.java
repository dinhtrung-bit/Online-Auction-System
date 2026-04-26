package server.models;
    public class ItemFactory {
        public static Item createItem(String CategoryInfo, int itemId, String name, double startingPrice, String description) {
            if (CategoryInfo == null) {
                return null;
            }
            switch (CategoryInfo.toUpperCase()) {
                case "ART":
                    return new Art(itemId,name,startingPrice,description);
                case "ELECTRONIC":
                    return new Electronics(itemId, name, startingPrice,description);
                case "VEHICLE":
                    return new Vehicle( itemId, name, description, startingPrice);
                default:
                    throw new IllegalArgumentException("Loại sản phẩm không hợp lệ: " + CategoryInfo);
            }
        }
    }

