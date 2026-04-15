package src.server.DAO;
import server.models.User;
public interface UserDAO extends GenericDAO<User> {
    // tìm người dùng để đăng nhập
    User findByUsername(String username) throws Exception;
}