package src.main.java.com.auctions.server.DAO;
import src.main.java.com.auctions.server.models.User;
public interface UserDAO extends GenericDAO<User> {
    // Thêm hàm đặc thù cho User (ví dụ: tìm người dùng để đăng nhập)
    User findByUsername(String username) throws Exception;
}