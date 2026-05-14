package server.networks.interfaces;

/**
 * Interface định nghĩa khả năng phát tin nhắn đến toàn bộ hệ thống.
 * Fix 3.10: Tách biệt logic nghiệp vụ (Service) khỏi logic mạng (Socket).
 */
public interface BroadcastChannel {
    /**
     * Gửi chuỗi JSON đến tất cả Client đang kết nối.
     * @param json Chuỗi dữ liệu định dạng JSON.
     */
    void broadcast(String json);
}