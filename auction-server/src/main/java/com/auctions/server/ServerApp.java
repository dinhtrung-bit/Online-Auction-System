package com.auctions.server;

import com.auctions.server.models.*;
import com.auctions.server.DAO.*; // Import gói DAO để gọi DBConnection

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerApp {
    private static final int PORT = 8080;

    // Dùng CopyOnWriteArrayList để Seller thêm hàng và Bidder xem hàng không bị xung đột
    public static List<AuctionRoom> allRooms = new CopyOnWriteArrayList<>();

    // Quản lý xem Client nào đang ở phòng nào (Cấm lưu null vào đây)
    public static Map<PrintWriter, AuctionRoom> clientRooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("--- KHỞI ĐỘNG SERVER ĐẤU GIÁ ---");

        // Bước 1: Khởi tạo/Kiểm tra kết nối Database MySQL
        initDatabase();

        // Bước 2: Tạo dữ liệu mẫu
        initDummyData();

        // Bước 3: Chạy luồng quét thời gian tự động
        startStatusRefreshThread();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[OK] Server đang trực chiến tại port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("[Lỗi] Server sập: " + e.getMessage());
        }
    }

    // THÊM HÀM NÀY: Kiểm tra kết nối DB khi Server khởi động
    private static void initDatabase() {
        try {
            if (DBConnection.getInstance().getConnection() != null) {
                System.out.println("[DB] Đã kết nối thành công tới MySQL.");
            } else {
                System.out.println("[DB] Lỗi: Không thể kết nối Database!");
            }
        } catch (Exception e) {
            System.out.println("[DB] Lỗi kết nối: " + e.getMessage());
        }
    }

    private static void initDummyData() {
        Item laptop = new Electronics("ITM01", "MacBook Pro M3", 1000.0, 12,"beatifull");
        allRooms.add(new AuctionRoom(1L, laptop, LocalDateTime.now().plusMinutes(30)));
        System.out.println("[Hệ thống] Đã nạp sản phẩm mẫu.");
    }

    private static void startStatusRefreshThread() {
        new Thread(() -> {
            while (true) {
                try {
                    for (AuctionRoom room : allRooms) {
                        if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
                            room.setStatus(AuctionStatus.FINISHED);
                            System.out.println("[Hết giờ] Sản phẩm " + room.getItem().getName() + " đã đóng phiên.");
                        }
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    public static synchronized void broadcastToRoom(AuctionRoom room, String msg) {
        for (Map.Entry<PrintWriter, AuctionRoom> entry : clientRooms.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(room)) {
                entry.getKey().println(msg);
            }
        }
    }

    // --- CLASS XỬ LÝ TỪNG CLIENT ---
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName = "Khách";

        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("LOGIN:")) {
                        clientName = message.split(":")[1];
                        System.out.println("[+] " + clientName + " đã vào hệ thống.");
                    }
                    else if (message.equals("LIST_REQ")) {
                        StringBuilder sb = new StringBuilder("ITEM_LIST:");
                        for (AuctionRoom room : allRooms) {
                            if(room.getStatus() != AuctionStatus.FINISHED) {
                                sb.append(room.getItem().getName()).append(",");
                            }
                        }
                        out.println(sb.toString());
                    }
                    else if (message.startsWith("JOIN:")) {
                        String itemName = message.split(":")[1];
                        for (AuctionRoom room : allRooms) {
                            if (room.getItem().getName().equals(itemName)) {
                                clientRooms.put(out, room);
                                out.println("CURRENT_BID:" + room.getCurrentPrice());
                                break;
                            }
                        }
                    }
                    else if (message.startsWith("BID:")) {
                        double bidAmount = Double.parseDouble(message.split(":")[1]);
                        AuctionRoom currentRoom = clientRooms.get(out);
                        if (currentRoom != null) {
                            try {
                                Bidder b = new Bidder("123", clientName, 999999.0);
                                currentRoom.placeBid(b, bidAmount);

                                // TẠM THỜI COMMENT: Khi nào bạn viết xong BidMessageDAO thì mở comment ra dùng
                                /*
                                BidMessageDAO bidDAO = new BidMessageDAO();
                                bidDAO.saveBid(currentRoom.getItem().getName(), clientName, bidAmount);
                                */

                                broadcastToRoom(currentRoom, "NEW_BID:" + clientName + ":" + currentRoom.getCurrentPrice());
                            } catch (Exception e) {
                                out.println("ERROR:" + e.getMessage());
                            }
                        }
                    }
                    else if (message.startsWith("ADD_ITEM:")) {
                        String[] p = message.split(":");
                        String name = p[1]; double price = Double.parseDouble(p[3]);


//                        //ItemDAO itemDAO = new ItemDAO();
//                        //itemDAO.saveItem(name, p[2], price, clientName);
//                        */

                        // Tạo phòng mới trên RAM
                        Item item = new Electronics("NEW", name, price, 12,"beatifull");
                        AuctionRoom room = new AuctionRoom((long)(allRooms.size()+1), item, LocalDateTime.now().plusMinutes(15));
                        allRooms.add(room);

                        out.println("SERVER_MSG:Đăng bán [" + name + "] thành công!");
                        System.out.println("[!] " + clientName + " vừa đăng bán: " + name);
                    }
                }
            } catch (IOException e) {
                System.out.println("[-] " + clientName + " thoát.");
            } finally {
                if (out != null) clientRooms.remove(out);
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}