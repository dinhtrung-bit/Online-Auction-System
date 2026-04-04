package com.auctions.server.network;

import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket clientSocket;    // tạo phương thức truyền dữ liệu từ client đi
    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override               // override hàm run của Runnable
    public void run() {     // cái này để chạy th.start() ở file bên kia
        try {
            System.out.println("Dang xu ly: " + clientSocket.getInetAddress());     // lấy ip cuả client
        } catch (Exception e) {
            e.printStackTrace();    // in ra lỗi
        }
    }
}