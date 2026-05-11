package client.networks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * ClientMain: quản lý kết nối Socket duy nhất tới Server.
 */
public class ClientMain {

    private static Socket socket;
    private static PrintWriter out;
    private static Thread listenerThread;
    private static volatile boolean running = false;

    // Map: action → callback. Dành cho các event thời gian thực (như UPDATE_PRICE)
    private static final Map<String, Consumer<String>> listeners = new ConcurrentHashMap<>();

    // Hàng đợi lưu các tin nhắn lẻ không có listener (Để phục vụ cho hàm receive() gọi đồng bộ)
    private static final BlockingQueue<String> syncResponseQueue = new LinkedBlockingQueue<>();

    // ─── Kết nối ──────────────────────────────────────────────────────────────

    public static synchronized void connectToServer() {
        try {
            if (socket != null && !socket.isClosed()) return;

            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("[Client] Đã kết nối tới Server!");
            startListenerThread(new BufferedReader(new InputStreamReader(socket.getInputStream())));
        } catch (Exception e) {
            System.err.println("[Client] Lỗi kết nối: " + e.getMessage());
        }
    }

    public static boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    // ─── Gửi và Nhận message (Đồng bộ) ────────────────────────────────────────

    public static void send(String jsonString) {
        if (out != null) {
            out.println(jsonString);
            System.out.println("[Client] Gửi: " + jsonString);
        } else {
            System.err.println("[Client] Chưa kết nối — không thể gửi.");
        }
    }

    /**
     * Chờ và nhận tin nhắn từ Server một cách đồng bộ (Block luồng cho đến khi có phản hồi).
     * Phục vụ cho các tác vụ như Login, Register...
     */
    public static String receive() throws InterruptedException {
        return syncResponseQueue.take();
    }

    // ─── Đăng ký / huỷ listener (Bất đồng bộ) ─────────────────────────────────

    public static void registerListener(String action, Consumer<String> callback) {
        listeners.put(action, callback);
    }

    public static void unregisterListener(String action) {
        listeners.remove(action);
    }

    // ─── Background thread đọc message từ Server ──────────────────────────────

    private static void startListenerThread(BufferedReader in) {
        running = true;
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                    final String json = line;
                    String action = extractAction(json);
                    String payload = extractPayload(json);

                    System.out.println("[Client] Nhận: action=" + action);

                    Consumer<String> cb = listeners.get(action);
                    if (cb != null) {
                        // Nếu có Controller đang lắng nghe action này (VD: màn hình AuctionList)
                        cb.accept(payload);
                    } else {
                        // Nếu KHÔNG có ai lắng nghe (VD: Server trả về LOGIN_SUCCESS)
                        // -> Đẩy vào Queue để hàm receive() nhặt lấy
                        syncResponseQueue.offer(json);
                    }
                }
            } catch (Exception e) {
                if (running) System.err.println("[Client] Mất kết nối Server: " + e.getMessage());
            }
        }, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // Parse action từ JSON string
    private static String extractAction(String json) {
        try {
            int i = json.indexOf("\"action\"");
            if (i < 0) return "";
            int q1 = json.indexOf('"', i + 9);
            int q2 = json.indexOf('"', q1 + 1);
            return json.substring(q1 + 1, q2);
        } catch (Exception e) { return ""; }
    }

    // Parse payload từ JSON
    private static String extractPayload(String json) {
        try {
            int i = json.indexOf("\"payload\"");
            if (i < 0) return "";
            int colon = json.indexOf(':', i + 9);
            String after = json.substring(colon + 1).trim();
            if (after.startsWith("\"")) {
                int end = after.lastIndexOf('"');
                return after.substring(1, end).replace("\\\"", "\"").replace("\\\\", "\\");
            } else {
                int end = after.lastIndexOf('}');
                return end >= 0 ? after.substring(0, end).trim() : after.trim();
            }
        } catch (Exception e) { return ""; }
    }
}