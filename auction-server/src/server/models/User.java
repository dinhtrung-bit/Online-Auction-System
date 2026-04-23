package server.models;
import java.io.Serializable;

// implements Serializable để gửi được qua Socket
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L; // Đảm bảo đồng nhất phiên bản
    protected int userId;
    protected String username;
    protected String role; // "BIDDER", "SELLER", "ADMIN"
protected String passwordhash;
    public User(int userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public abstract void displayDashboard(); // Tính trừu tượng: mỗi vai trò hiện một menu khác nhau [cite: 122]

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
    public String getPasswordHash(){
        return passwordhash;
    }
}



