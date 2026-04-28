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
├── auction-server/
├── pom.xml
└── src/
    ├── main/java/server/
    │   ├── application/            # 1. Điểm khởi chạy Server
    │   │   └── ServerApplication.java  (Đổi tên từ ServerRocket.java)
    │   │
    │   ├── network/                # 2. Tầng giao tiếp mạng (Socket)
    │   │   ├── ClientHandler.java
    │   │   └── dto/
    │   │       └── MessageDTO.java     (Nơi chứa các đối tượng gửi/nhận qua mạng)
    │   │
    │   ├── services/               # 3. Tầng Business Logic (Bộ não xử lý)
    │   │   └── AuctionManager.java     (Chuyển từ package 'auction' sang đây)
    │   │
    │   ├── dao/                    # 4. Tầng truy xuất dữ liệu (Giao tiếp Database)
    │   │   ├── DBConnection.java
    │   │   ├── GenericDAO.java
    │   │   ├── interfaces/             (Gom các Interface lại cho gọn)
    │   │   │   ├── UserDAO.java
    │   │   │   ├── ItemDAO.java
    │   │   │   ├── AuctionRoomDAO.java
    │   │   │   └── BidMessageDAO.java
    │   │   └── impl/                   (Gom các class triển khai Interface)
    │   │       ├── UserDAOImpl.java    (Sửa chữ 'i' thường thành 'I' hoa)
    │   │       ├── ItemDAOImpl.java    (Sửa chữ 'i' thường thành 'I' hoa)
    │   │       └── AuctionRoomDAOImpl.java
    │   │
    │   ├── models/                 # 5. Tầng Dữ liệu (Entities/Models)
    │   │   ├── users/
    │   │   │   ├── User.java, Admin.java, Bidder.java, Seller.java, UserFactory.java
    │   │   ├── items/
    │   │   │   ├── Item.java, Art.java, Electronics.java, Vehicle.java, ItemFactory.java
    │   │   └── auction/
    │   │       ├── AuctionRoom.java, AuctionStatus.java, BidMessage.java
    │   │
    │   ├── exceptions/             # 6. Tầng xử lý lỗi ngoại lệ
    │   │   └── InvalidBidException.java
    │   │
    │   └── utils/                  # 7. Các công cụ hỗ trợ dùng chung
    │       └── Validation.java         (Đổi tên package từ 'ultis' đang bị sai chính tả)
    │
    └── test/java/server/           # 8. TẦNG KIỂM THỬ (Tách hoàn toàn khỏi luồng chạy chính)
        └── dao/
            ├── TestDB.java
            ├── TestUser.java       (Đổi tên từ testUser)
            └── TestItem.java       (Đổi tên từ testItem)
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