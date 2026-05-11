package server.networks.handlers;

import server.models.users.User;

/**
 * UserHolder — wrapper đơn giản để ClientHandler và các handler
 * cùng chia sẻ tham chiếu đến user đang đăng nhập (loggedInUser).
 *
 * Dùng wrapper thay vì truyền User[] hoặc AtomicReference để code rõ hơn.
 */
public class UserHolder {
    private User user;

    public UserHolder() {}

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}