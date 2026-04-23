package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Tải file giao diện đăng nhập (FXML)
            // Lưu ý: Đường dẫn bắt đầu bằng "/" để tìm từ thư mục src
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/login.fxml"));
            Parent root = loader.load();

            // 2. Thiết lập tiêu đề và Scene (màn hình) cho cửa sổ
            primaryStage.setTitle("Hệ thống Đấu giá Trực tuyến - Đăng nhập");
            primaryStage.setScene(new Scene(root));

            // 3. Một số tùy chỉnh giao diện (tùy chọn)
            primaryStage.setResizable(false); // Không cho phóng to cửa sổ

            // 4. Hiển thị cửa sổ
            primaryStage.show();

            System.out.println("[Client] 🚀 Giao diện JavaFX đã khởi động thành công.");

        } catch (Exception e) {
            System.err.println("[Lỗi] Không thể tải file login.fxml. Kiểm tra lại đường dẫn trong views!");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Hàm này sẽ gọi đến phương thức start() ở trên
        launch(args);
    }
}