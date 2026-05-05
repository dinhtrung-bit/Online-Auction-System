package server.models.users;

import server.dao.impl.UserDAOimpl;
import server.services.PasswordUtil;

public class CreateAdmin {
    public static void main(String[] args) throws Exception {
        User admin = UserFactory.createUser("ADMIN", 0, "admin");

        admin.setPasswordHash(PasswordUtil.hash("123456"));

        new UserDAOimpl().insert(admin);

        System.out.println("Tạo admin thành công");
    }
}