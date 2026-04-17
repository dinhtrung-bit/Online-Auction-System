<<<<<<< HEAD
<<<<<<< HEAD
package src.server.models;
=======
package server.models;
>>>>>>> dinhtrung
=======
package src.server.models;
>>>>>>> thắng
import java.io.Serializable;

// implements Serializable để gửi được qua Socket [cite: 1436]
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L; // Đảm bảo đồng nhất phiên bản [cite: 1447]
    protected String userId;
    protected String username;
    protected String role; // "BIDDER", "SELLER", "ADMIN"
    protected String password_hash;
    public User(String userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public abstract void displayDashboard(); // Tính trừu tượng: mỗi vai trò hiện một menu khác nhau [cite: 122]

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
    public String getPasswordHash(){ return password_hash ;}
}



