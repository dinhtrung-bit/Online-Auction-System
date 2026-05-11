package client.networks;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * ClientMain — quản lý kết nối Socket duy nhất tới Server.
 *
 * Thay đổi so với phiên bản cũ:
 *
 * [Fix 1] Dùng Gson parse thay vì extractAction/extractPayload thủ công.
 *   Trước: manual string index → sai với JSON lồng nhau.
 *   Sau  : Gson.fromJson(line, MessageDTO.class) → luôn đúng.
 *
 * [Fix 2] Multi-listener: Map<String, List<Consumer>> thay vì Map<String, Consumer>.
 *   Trước: listener sau ghi đè listener trước cùng action.
 *   Sau  : nhiều screen có thể cùng lắng nghe "ERROR", "AUCTION_CANCELED"...
 *   unregisterListener(action, callback) xóa đúng callback, không xóa toàn bộ action.
 */
public class ClientMain {

    private static final Gson GSON = new Gson();

    private static Socket       socket;
    private static PrintWriter  out;
    private static Thread       listenerThread;
    private static volatile boolean running = false;

    // [Fix 2] Multi-listener: mỗi action có thể có nhiều callback
    private static final Map<String, List<Consumer<String>>> listeners =
            new ConcurrentHashMap<>();

    // Queue cho receive() đồng bộ (Login, Register... không dùng listener)
    private static final BlockingQueue<String> syncResponseQueue =
            new LinkedBlockingQueue<>();

    // ── Kết nối ─────────────────────────────────────────────────────

    // [Fix Lag] Timeout kết nối: 5 giây thay vì block vô hạn (mặc định TCP ~75s)
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    public static synchronized void connectToServer() {
        try {
            if (socket != null && !socket.isClosed()) return;
            socket = new Socket();
            socket.connect(
                    new java.net.InetSocketAddress("localhost", 8080),
                    CONNECT_TIMEOUT_MS
            );
            out    = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("[Client] Đã kết nối tới Server!");
            startListenerThread(
                    new BufferedReader(new InputStreamReader(socket.getInputStream())));
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("[Client] Kết nối timeout sau " + CONNECT_TIMEOUT_MS + "ms: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Client] Lỗi kết nối: " + e.getMessage());
        }
    }

    public static boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    // ── Gửi / Nhận ──────────────────────────────────────────────────

    public static void send(String jsonString) {
        if (out != null) {
            out.println(jsonString);
            System.out.println("[Client] Gửi: " + jsonString);
        } else {
            System.err.println("[Client] Chưa kết nối — không thể gửi.");
        }
    }

    /** Chờ phản hồi đồng bộ (dùng cho Login, Register). */
    public static String receive() throws InterruptedException {
        return syncResponseQueue.take();
    }

    // ── Listener API — Multi-listener ───────────────────────────────

    /**
     * Đăng ký callback cho một action.
     * Nhiều callback có thể đăng ký cùng action — không ghi đè nhau.
     */
    public static void registerListener(String action, Consumer<String> callback) {
        listeners.computeIfAbsent(action, k -> new CopyOnWriteArrayList<>())
                .add(callback);
    }

    /**
     * Hủy đúng callback đã đăng ký — không ảnh hưởng các callback khác cùng action.
     */
    public static void unregisterListener(String action, Consumer<String> callback) {
        List<Consumer<String>> list = listeners.get(action);
        if (list != null) list.remove(callback);
    }

    /**
     * Hủy toàn bộ listener của một action (dùng khi rời màn hình).
     */
    public static void unregisterAllListeners(String action) {
        listeners.remove(action);
    }

    /**
     * Alias 1-arg: hủy toàn bộ listener của action.
     * Giữ lại để các Controller hiện tại không cần sửa.
     * Nếu muốn xóa chính xác 1 callback, dùng unregisterListener(action, callback).
     */
    public static void unregisterListener(String action) {
        listeners.remove(action);
    }

    // ── Background listener thread ───────────────────────────────────

    private static void startListenerThread(BufferedReader in) {
        running = true;
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                    final String json = line;

                    // [Fix 1] Dùng Gson thay vì manual string parsing
                    MessageDTO dto = GSON.fromJson(json, MessageDTO.class);
                    if (dto == null) continue;

                    String action  = dto.getAction()  != null ? dto.getAction()  : "";
                    String payload = dto.getPayload()  != null ? dto.getPayload() : "";

                    System.out.println("[Client] Nhận: action=" + action);

                    List<Consumer<String>> callbacks = listeners.get(action);
                    if (callbacks != null && !callbacks.isEmpty()) {
                        // [Fix 2] Gọi tất cả callback đã đăng ký cho action này
                        for (Consumer<String> cb : callbacks) {
                            cb.accept(payload);
                        }
                    } else {
                        // Không có listener → đẩy vào queue để receive() nhặt
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
}