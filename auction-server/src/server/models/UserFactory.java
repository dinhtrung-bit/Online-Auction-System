package server.models;

public class UserFactory {
    public static User createUser(String role, String userId, String username) {
        if (role == null) return null;

        switch (role.toUpperCase()) {
            case "BIDDER":
                return new Bidder(userId, username,0.0);
            case "SELLER":
                return new Seller(userId, username);
            case "ADMIN":
                return new Admin(userId, username);
            default:
                return null;
        }
    }
}
