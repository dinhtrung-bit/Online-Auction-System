package server.networks;

import server.DAO.DBConnection;

import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;

public class ServerMain {
        public static void main(String[] args) {
            try {
                // 1. Test kết nối database trước
                Connection conn = DBConnection.getInstance().getConnection();
                System.out.println("Kết nối database thành công!");

                // 2. Mở server sau khi DB đã kết nối OK
                ServerSocket serverSocket = new ServerSocket(8888);
                System.out.println("Server đang chạy ở cổng 8888...");

                // 3. Chờ client kết nối
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client đã kết nối!");

                    ClientHandler handler = new ClientHandler(clientSocket);
                    new Thread(handler).start();
                }

            } catch (Exception e) {
                System.out.println("Lỗi khi khởi động server!");
                e.printStackTrace();
            }
        }
    }

