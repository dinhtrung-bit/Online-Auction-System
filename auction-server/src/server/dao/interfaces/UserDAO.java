package server.dao.interfaces;
import server.dao.core.GenericDAO;
import server.models.users.User;

import java.math.BigDecimal;

public interface UserDAO extends GenericDAO<User> {
    // tìm người dùng để đăng nhập
    User findByUsername(String username) throws Exception;
    boolean transferMoney(int fromUserId, int toUserId, BigDecimal amount);
}
