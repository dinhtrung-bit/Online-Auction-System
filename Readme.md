# 🏷️ Hệ thống Đấu giá Trực tuyến (Java Online Auction System)

Dự án Hệ thống Đấu giá Trực tuyến được phát triển theo mô hình Client-Server, áp dụng các nguyên lý thiết kế hướng đối tượng (OOP), Design Patterns và kiến trúc MVC.

---

## 👥 1. Phân công công việc

| Thành viên | Vai trò cốt lõi | Trách nhiệm chính |
| :--- | :--- | :--- |
| **Thành viên 1 (Leader)** | **Backend 1 (Core Logic & Architecture)** | Thiết kế kiến trúc OOP, áp dụng Design Pattern, xử lý các thuật toán đấu giá phức tạp (Auto-bid, Anti-sniping). Quản lý tiến độ. |
| **Thành viên 2** | **Backend 2 (Business & Testing)** | Xây dựng nghiệp vụ cơ bản (CRUD), quản lý trạng thái phiên đấu giá, xử lý lỗi ngoại lệ và viết Unit Test (JUnit). |
| **Thành viên 3** | **Frontend (JavaFX UI/UX)** | Xây dựng giao diện đồ họa, tích hợp Client MVC, xử lý hiển thị Realtime và vẽ biểu đồ lịch sử giá (Observer Pattern). |
| **Thành viên 4** | **Server/DB (Network & DevOps)** | Xây dựng Server MVC, giao tiếp Socket/REST, quản lý Database, xử lý đa luồng (Concurrent Bidding) và thiết lập CI/CD. |

### 📌 Chi tiết nhiệm vụ:
- **Thành viên 1:** Thiết kế class `Entity`, `Item`, `User`, `Auction`. Áp dụng `Singleton`, `Factory Method`, `Strategy`. Code thuật toán Auto-Bidding và Anti-sniping (gia hạn Y giây khi có bid trong X giây cuối).
- **Thành viên 2:** Logic phân quyền (Admin, Seller, Bidder), CRUD sản phẩm. Quản lý vòng đời phiên đấu giá (`OPEN` → `RUNNING` → `FINISHED` → `PAID/CANCELED`). Bắt lỗi logic.
- **Thành viên 3:** Code các file `.fxml` cho màn hình Login, Dashboard, Realtime Bidding. Cập nhật UI không cần reload khi có bid mới. Vẽ biểu đồ Line chart.
- **Thành viên 4:** Thiết kế DB Schema. Code Server nhận/gửi JSON qua Socket/REST. Xử lý tranh chấp dữ liệu (Lost update) khi nhiều người bid cùng lúc. Setup Maven & GitHub Actions.

---

## 📂 2. Cấu trúc thư mục (Project Structure)

Dự án sử dụng **Maven** và được chia thành 2 module chính: `auction-client` và `auction-server` để đảm bảo tính độc lập.

```text
online-auction-system/
├── README.md
├── .gitignore
├── auction-server/                 # Module xử lý Server & Database
│   ├── pom.xml
│   └── src/server/
│       ├── MainServer.java         # Entry point bật server
│       ├── controller/             # Nhận request từ Client (Socket/API)
│       ├── model/                  # Entities (User, Item, Auction, Bid)
│       ├── dao/                    # Database Access Object (truy vấn DB)
│       ├── service/                # Business Logic, Auto-Bidding, Anti-sniping
│       └── utils/                  # DB Connection, ThreadPool, Constants
│
└── auction-client/                 # Module giao diện UI (JavaFX)
    ├── pom.xml
    ├── src/client/
    │   ├── MainApp.java            # Entry point chạy UI
    │   ├── controller/             # UI Controllers (Xử lý sự kiện click, input)
    │   ├── model/                  # Data models nội bộ của Client
    │   ├── network/                # Gửi/nhận dữ liệu từ Server (Socket Client)
    │   └── utils/                  # Cấu hình UI, Alerts
    └── src/resources/
        ├── views/                  # Các file giao diện .fxml (Login.fxml, Bidding.fxml...)
        ├── styles/                 # CSS cho JavaFX
        └── assets/                 # Hình ảnh, icons