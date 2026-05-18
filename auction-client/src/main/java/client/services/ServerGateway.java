package client.services;

import client.networks.ClientMain;
import client.networks.MessageDTO;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ServerGateway — wrapper gọn cho {@link ClientMain} để các controller
 * không phải lặp lại pattern:
 *
 * <pre>
 *   new Thread(() -> ClientMain.send(gson.toJson(new MessageDTO("X", json)))).start();
 *   ClientMain.registerListener("Y", payload -> {
 *       List<Foo> data = gson.fromJson(payload, type);
 *       Platform.runLater(() -> ...);
 *   });
 * </pre>
 *
 * <p>Tự động:
 * <ul>
 *   <li>{@link #send(String, Object)} — serialise payload → JSON → MessageDTO → JSON → gửi.
 *   <li>{@link #sendAsync(String, Object)} — gửi trong thread riêng tránh block UI.
 *   <li>{@link #on(String, Class, Consumer)} — đăng ký listener parse sẵn payload sang object.
 *   <li>{@link #onList(String, Class, Consumer)} — listener cho payload là {@code List<T>}.
 *   <li>{@link #onMap(String, Consumer)} — listener cho payload là Map.
 *   <li>{@link #onString(String, Consumer)} — listener payload chuỗi thô.
 * </ul>
 *
 * <p>Tất cả callback đều được tự động đẩy về JavaFX Application Thread
 * bằng {@link Platform#runLater}.
 */
public final class ServerGateway {

    private static final Gson GSON = new Gson();

    private ServerGateway() {}

    // ─── Send ───────────────────────────────────────────────────────

    /** Gửi message với payload là object (sẽ được serialize sang JSON). */
    public static void send(String action, Object payload) {
        String body = payload == null ? "" :
                (payload instanceof String s ? s : GSON.toJson(payload));
        ClientMain.send(GSON.toJson(new MessageDTO(action, body)));
    }

    /** Gửi message trong một thread riêng để không chặn JavaFX Application Thread. */
    public static void sendAsync(String action, Object payload) {
        new Thread(() -> send(action, payload), "gw-send-" + action).start();
    }

    /** Đảm bảo đã kết nối tới server. */
    public static void ensureConnected() {
        ClientMain.connectToServer();
    }

    // ─── Listener API ───────────────────────────────────────────────

    /** Đăng ký listener raw payload (String). Callback chạy trên JavaFX thread. */
    public static void onString(String action, Consumer<String> handler) {
        ClientMain.registerListener(action, payload ->
                Platform.runLater(() -> handler.accept(payload)));
    }

    /** Đăng ký listener parse sẵn payload sang class {@code T}. */
    public static <T> void on(String action, Class<T> type, Consumer<T> handler) {
        ClientMain.registerListener(action, payload -> {
            T obj = null;
            try { obj = GSON.fromJson(payload, type); }
            catch (JsonSyntaxException ignored) {}
            final T finalObj = obj;
            Platform.runLater(() -> handler.accept(finalObj));
        });
    }

    /** Đăng ký listener parse payload sang {@code List<T>}. */
    @SuppressWarnings("unchecked")
    public static <T> void onList(String action, Class<T> elementType, Consumer<List<T>> handler) {
        ClientMain.registerListener(action, payload -> {
            List<T> list = null;
            try {
                Type t = TypeToken.getParameterized(List.class, elementType).getType();
                list = (List<T>) GSON.fromJson(payload, t);
            } catch (JsonSyntaxException ignored) {}
            final List<T> finalList = list;
            Platform.runLater(() -> handler.accept(finalList));
        });
    }

    /** Đăng ký listener payload là {@code Map<String,Object>}. */
    public static void onMap(String action, Consumer<Map<String, Object>> handler) {
        ClientMain.registerListener(action, payload -> {
            Map<String, Object> data = null;
            try {
                Type t = new TypeToken<Map<String, Object>>() {}.getType();
                data = GSON.fromJson(payload, t);
            } catch (JsonSyntaxException ignored) {}
            final Map<String, Object> finalData = data;
            Platform.runLater(() -> handler.accept(finalData));
        });
    }

    /** Đăng ký listener payload là {@code List<Map<String,Object>>}. */
    public static void onMapList(String action, Consumer<List<Map<String, Object>>> handler) {
        ClientMain.registerListener(action, payload -> {
            List<Map<String, Object>> data = null;
            try {
                Type t = new TypeToken<List<Map<String, Object>>>() {}.getType();
                data = GSON.fromJson(payload, t);
            } catch (JsonSyntaxException ignored) {}
            final List<Map<String, Object>> finalData = data;
            Platform.runLater(() -> handler.accept(finalData));
        });
    }

    // ─── Cleanup ────────────────────────────────────────────────────

    /** Gỡ một loạt action listener (dùng khi rời màn hình). */
    public static void off(String... actions) {
        for (String a : actions) ClientMain.unregisterListener(a);
    }
}
