# BaiTapLon_UET_LTNC
Hệ thống đấu giá trực tuyến - Bài tập lớn Lập trình nâng cao UET
# 🔨 Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-GUI-blue?style=for-the-badge)
![Socket](https://img.shields.io/badge/Networking-Socket-green?style=for-the-badge)
![GitHub Actions](https://img.shields.io/badge/CI%2FCD-Passing-success?style=for-the-badge&logo=github-actions)

Đây là dự án Bài Tập Lớn môn Lập Trình Nâng Cao. [cite_start]Hệ thống cho phép người dùng tham gia cạnh tranh giá để mua một sản phẩm trong khoảng thời gian xác định [cite: 23-25]. [cite_start]Dự án được thiết kế theo kiến trúc **Client-Server** phân tầng rõ ràng[cite: 125].

---

## 🏗 Kiến Trúc Hệ Thống
* **Phía Server:** Chạy logic cốt lõi, quản lý kết nối đồng thời và xử lý giao dịch. [cite_start]Áp dụng mô hình Controller-Model-DAO[cite: 129].
* [cite_start]**Phía Client:** Giao diện người dùng đồ họa (GUI) xây dựng bằng **JavaFX**, giao tiếp với Server qua chuẩn dữ liệu JSON / Socket[cite: 126, 128].
* [cite_start]**Realtime Update:** Tích hợp Observer Pattern để đẩy thông báo giá mới nhất đến tất cả Client ngay lập tức [cite: 95-98].

---

## 👥 Đội Ngũ Phát Triển (Nhóm 4)

| Thành viên | Vai trò chính | Trách nhiệm cốt lõi |
| :--- | :--- | :--- |
| **[Tên P1]** | Server Core & Network | Quản lý ServerSocket, ThreadPool, Xử lý đồng bộ (Concurrency). |
| **[Tên P2]** | Database & Model | Thiết kế OOP Entity (User, Item), DAO layer. |
| **[Tên P3]** | Frontend GUI | Thiết kế giao diện JavaFX, Event Controller, Chart. |
| **[Tên P4]** | Business Logic & QA | Thuật toán Đấu giá (Auto-bid), Quản lý Exception, JUnit Test, CI/CD. |

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Dự Án

Dự án yêu cầu **Java JDK 17+** và **Maven**.
## Quy Tắc Đóng Góp (Dành cho thành viên nhóm)
Nhóm áp dụng chuẩn Conventional Commits. Vui lòng đặt tên commit theo định dạng sau:

feat: [Mô tả] - Thêm tính năng mới (vd: feat: Thêm chức năng Auto-bidding)

fix: [Mô tả] - Sửa lỗi (bug)

test: [Mô tả] - Viết hoặc sửa Unit Test


refactor: [Mô tả] - Tối ưu hóa code, chuẩn hóa theo Google Style Guide.

Lưu ý: Mọi Pull Request / Push sẽ được tự động kiểm thử bởi GitHub Actions trước khi merge

### Bước 1: Khởi động Server
Mở terminal, di chuyển vào thư mục `auction-server` và chạy lệnh:
```bash
cd auction-server
mvn clean compile exec:java -Dexec.mainClass="com.auction.server.ServerApp"

### Bước 2: Khởi động Client
Mở một terminal khác, di chuyển vào thư mục auction-client và chạy lệnh:
cd auction-client
mvn clean compile javafx:run
