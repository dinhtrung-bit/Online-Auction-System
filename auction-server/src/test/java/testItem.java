import server.dao.interfaces.ItemDAO;
import server.dao.impl.ItemDAOimpl;
import server.models.items.Item;
import server.models.items.ItemFactory;
import server.models.users.Seller;

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
