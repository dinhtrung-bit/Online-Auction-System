package server.networks;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestServer {
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8080);
        System.out.println("Server is running on port 8080...");
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();  //sử dụng virtual thread
        while (true) {
            Socket client = server.accept();
            System.out.println("New client connected: " + client.getInetAddress());

            // Bàn giao cho Thread Pool xử lý
            pool.execute(new ClientHandler(client));
        }
    }
}