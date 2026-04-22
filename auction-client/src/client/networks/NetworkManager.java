package client.networks;

import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkManager {
    private static NetworkManager instance;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Gson gson = new Gson();
    private Consumer<MessageDTO> responseHandler;

    private NetworkManager() {
        try {
            // Kết nối tới ServerRocket đang chạy ở port 8080
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Luồng lắng nghe phản hồi từ Server liên tục
            new Thread(this::listen).start();
        } catch (IOException e) {
            System.err.println("Không thể kết nối tới Server!");
        }
    }

    public static NetworkManager getInstance() {
        if (instance == null) instance = new NetworkManager();
        return instance;
    }

    public void sendRequest(String action, String payload, Consumer<MessageDTO> callback) {
        this.responseHandler = callback;
        MessageDTO msg = new MessageDTO(action, payload);
        out.println(gson.toJson(msg));
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                MessageDTO response = gson.fromJson(line, MessageDTO.class);
                if (responseHandler != null) {
                    responseHandler.accept(response);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}