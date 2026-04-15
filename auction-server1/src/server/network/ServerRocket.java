package server.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerRocket {
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8080);           // mở cổng 8080 trên server để nhận dữ liệu truyền tới
        System.out.println("Server is running on port 8080...");

        while (true) {          // chạy liên tục
            Socket client = server.accept();        // blocking đến khi có client kết nối tới
            System.out.println("New client connected!");
            ClientHandler handler = new ClientHandler(client) ;  // bàn giao client vừa kết nối để quay lại chờ client tiếp theo
            Thread th = new Thread (handler) ;              // mang client đến 1 luồng thread mới
            th.start() ;            //run
        }
    }
}