package server.models.users;

import java.math.BigDecimal;


public class Admin extends User {


    public Admin() {
        super();
    }

    public Admin(int userId, String username, String passwordHash, String email, BigDecimal accountBalance) {
        super(userId, username, passwordHash, email, accountBalance);

    }

    @Override
    public String getRole() {
        return "ADMIN";
    }


}