package server.models.users;

import server.dao.impl.UserDAOImpl;
import server.services.PasswordUtil;
// 1 tài khoản admin cố định sẵn

public class CreateAdmin {
    public static void main(String[] args) throws Exception {
        User admin = UserFactory.createUser("ADMIN", 0, "admin");

        admin.setPasswordHash(PasswordUtil.hash("123456"));

        new UserDAOImpl().insert(admin);

        System.out.println("Tạo admin thành công");
    }
}