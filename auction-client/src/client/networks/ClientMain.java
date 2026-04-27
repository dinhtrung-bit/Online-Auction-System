package client.networks;

import com.google.gson.Gson;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

import static java.lang.System.out;

public class ClientMain {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 8080);//kết nôí tới server
            Scanner sc = new Scanner(System.in) ;
            MessageDTO message = new MessageDTO("BID", "50000");        // tạo hành động của client kèm dữ liệu
            Gson gson = new Gson(); // khởi tạo gson
            String jsonString = gson.toJson(message);       // ép đối tượng thành chuỗi json
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);      // khởi tạo socket truyền đi true là truyền đi
            out.println(jsonString);    // truyền đi
            System.out.println("Da gui: " + jsonString);    // in ra đã truyền
            out.close() ;               // đóng file và ngắt kết nối tới server
            socket.close() ;
        } catch (Exception e) {
            e.printStackTrace();        // in lỗi
        }
    }
}