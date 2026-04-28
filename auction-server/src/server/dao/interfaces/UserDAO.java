package server.dao.interfaces;
import server.dao.core.GenericDAO;
import server.models.users.User;
public interface UserDAO extends GenericDAO<User> {
    // tìm người dùng để đăng nhập
    User findByUsername(String username) throws Exception;
}