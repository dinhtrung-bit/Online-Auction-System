import server.dao.interfaces.UserDAO;
import server.dao.impl.UserDAOimpl;

public class testUser {
    public static void main(String[] args) {
        try {
            UserDAO userDAO = new UserDAOimpl();

           // User user = new Bidder(0, "bidder_test", 2000.0);
//User user=new Seller(6,"Thắng");
            userDAO.delete(6);

            System.out.println("thay đổi user thành công!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}