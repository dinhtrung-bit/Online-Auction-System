package client.models;

public class UserViewModel {
    private int id;
    private String username;
    private String role;
    private String status;

    public UserViewModel(int id, String username, String role, String status) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.status = status;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
}