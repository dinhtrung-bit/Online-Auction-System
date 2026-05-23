package client.services;

import client.networks.ClientMain;
import javafx.application.Platform;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * RequestResponse — pattern "request → success | failed | timeout"
 * dùng chung cho Login / Register / Deposit / AddItem / UpdateItem /
 * DeleteItem / CreateAuction / DeleteAuction / SetAutoBid / CancelAutoBid.
 *
 * <p>Refactor từ những đoạn lặp lại trong các controllers:
 * <pre>
 *   ClientMain.registerListener("X_SUCCESS", payload -> { unregister... });
 *   ClientMain.registerListener("X_FAILED",  payload -> { unregister... });
 *   // gửi request
 * </pre>
 *
 * <p>Lợi ích:
 * <ul>
 *   <li>Tự cleanup cả 2 listener (SUCCESS + FAILED) khi 1 trong 2 nổ.
 *   <li>Tự cancel khi timeout — tránh listener treo lâu.
 *   <li>Callback luôn chạy trên JavaFX thread.
 * </ul>
 *
 * <p>Cách dùng:
 * <pre>
 *   RequestResponse.exchange()
 *       .request("LOGIN", payloadJson)
 *       .onSuccess(p -> ...)
 *       .onFailed(p  -> ...)
 *       .onTimeout(() -> ...)
 *       .timeoutMs(10_000)
 *       .send();
 * </pre>
 */
public final class RequestResponse {

    public static final long DEFAULT_TIMEOUT_MS = 10_000L;

    private String action;
    private String payload;
    private String successAction;
    private String failedAction;
    private Consumer<String> onSuccess = p -> {};
    private Consumer<String> onFailed  = p -> {};
    private Runnable onTimeout         = () -> {};
    private long timeoutMs             = DEFAULT_TIMEOUT_MS;

    private RequestResponse() {}

    /** Bắt đầu builder. */
    public static RequestResponse exchange() { return new RequestResponse(); }

    /**
     * Đặt action + payload và tự suy ra tên action success/failed
     * theo convention {@code ACTION_SUCCESS} / {@code ACTION_FAILED}.
     */
    public RequestResponse request(String action, String payload) {
        this.action  = action;
        this.payload = payload;
        if (this.successAction == null) this.successAction = action + "_SUCCESS";
        if (this.failedAction  == null) this.failedAction  = action + "_FAILED";
        return this;
    }

    /** Override tên action SUCCESS (mặc định {@code ACTION_SUCCESS}). */
    public RequestResponse successAction(String name) { this.successAction = name; return this; }
    /** Override tên action FAILED. */
    public RequestResponse failedAction(String name)  { this.failedAction  = name; return this; }

    public RequestResponse onSuccess(Consumer<String> h) { this.onSuccess = h; return this; }
    public RequestResponse onFailed(Consumer<String> h)  { this.onFailed  = h; return this; }
    public RequestResponse onTimeout(Runnable h)         { this.onTimeout = h; return this; }
    public RequestResponse timeoutMs(long ms)            { this.timeoutMs = ms; return this; }

    /** Gửi request và đăng ký listener. */
    public void send() {
        String successAction = action + "_SUCCESS";
        String failedAction  = action + "_FAILED";

        final AtomicBoolean handled = new AtomicBoolean(false);
        final Timer timer = new Timer(true);

        final Consumer<String>[] successRef = new Consumer[1];
        final Consumer<String>[] failedRef = new Consumer[1];

        Runnable cleanup = () -> {
            if (successRef[0] != null) ClientMain.unregisterListener(successAction, successRef[0]);
            if (failedRef[0]  != null) ClientMain.unregisterListener(failedAction,  failedRef[0]);
        };

        successRef[0] = payloadIn -> {
            if (!handled.compareAndSet(false, true)) return;
            timer.cancel();
            cleanup.run();
            Platform.runLater(() -> onSuccess.accept(payloadIn));
        };

        failedRef[0] = payloadIn -> {
            if (!handled.compareAndSet(false, true)) return;
            timer.cancel();
            cleanup.run();
            Platform.runLater(() -> onFailed.accept(payloadIn));
        };

        timer.schedule(new TimerTask() {
            @Override public void run() {
                if (handled.compareAndSet(false, true)) {
                    cleanup.run();
                    Platform.runLater(onTimeout);
                }
            }
        }, timeoutMs);

        ClientMain.registerListener(successAction, successRef[0]);
        ClientMain.registerListener(failedAction, failedRef[0]);

        ServerGateway.sendAsync(action, payload);
    }


}
