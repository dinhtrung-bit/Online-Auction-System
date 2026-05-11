package server.services;

import server.dao.impl.UserDAOImpl;
import server.dao.interfaces.UserDAO;
import server.models.users.User;
import server.models.users.UserFactory;

public class UserService {

    private final UserDAO userDAO = new UserDAOImpl();

    public void register(String username, String password, String role) throws Exception {
        User user = UserFactory.createUser(role, 0, username);

        // HASH Ở ĐÂY
        String hash = PasswordUtil.hash(password);
        user.setPasswordHash(hash);

        userDAO.insert(user);
    }

    public User login(String username, String password) throws Exception {
        User user = userDAO.findByUsername(username);

        if (user == null) return null;

        if (PasswordUtil.verify(password, user.getPasswordHash())) {
            return user;
        }

        return null;
    }
}