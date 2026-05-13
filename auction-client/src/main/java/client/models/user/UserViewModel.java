package client.models.user;

public class UserViewModel {
    private int id;
    private String username;
    private String role;
    private String status;
    private double balance;

    public UserViewModel() {}

    public UserViewModel(int id, String username, String role, String status) {
        this(id, username, role, status, 0);
    }

    public UserViewModel(int id, String username, String role, String status, double balance) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.status = status;
        this.balance = balance;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public double getBalance() { return balance; }

    public void setId(int id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setRole(String role) { this.role = role; }
    public void setStatus(String status) { this.status = status; }
    public void setBalance(double balance) { this.balance = balance; }
}
