package server.DAO;


import server.models.Item;
import server.models.ItemFactory;
import server.models.Seller;

public class testItem {
    public static void main(String[] args) {
        try {
            ItemDAO item = new ItemDAOimpl();
            Seller testSeller = new Seller(1,"Thắng");
           Item newItem= ItemFactory.createItem("ART",1,"tranh sơn dầu",1000,"đẹp như thật");
            item.insert(newItem);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
