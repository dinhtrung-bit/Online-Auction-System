package server.DAO;

import server.models.Bidder;
import server.models.User;

public class testUser {
    public static void main(String[] args) {
        try {
            UserDAO userDAO = new UserDAOimpl();

            User user = new Bidder(0, "bidder_test", 2000.0);

            userDAO.insert(user);

            System.out.println("Insert user thành công!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}