# 🏷️ Hệ thống Đấu giá Trực tuyến — AuctionVN (Online Auction System)

> Bài tập lớn môn **Lập trình Nâng cao** — Năm học 2025–2026
> Đề tài: Xây dựng hệ thống đấu giá trực tuyến hoàn chỉnh theo mô hình **Client–Server**, áp dụng **OOP**, **Design Patterns**, **Concurrency** và **JavaFX MVC**.

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-17-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![Maven](https://img.shields.io/badge/Maven-3.8+-red)
![HikariCP](https://img.shields.io/badge/HikariCP-5.1.0-success)
![License](https://img.shields.io/badge/License-Educational-lightgrey)

---

## 📌 1. Mô tả bài toán & Phạm vi hệ thống

**AuctionVN** là một hệ thống đấu giá trực tuyến cho phép nhiều người dùng cùng tham gia đặt giá cạnh tranh trên cùng một sản phẩm trong một khoảng thời gian xác định. Hệ thống được triển khai theo mô hình **Client–Server**, giao tiếp qua **TCP Socket** với định dạng **JSON**.

**Phạm vi hệ thống** bao gồm 3 vai trò người dùng:
- 👤 **Bidder** — Người tham gia đấu giá, có ví điện tử, có thể đặt giá thủ công hoặc bật **Auto-Bid**.
- 🏪 **Seller** — Người bán, đăng sản phẩm và mở phiên đấu giá.
- 🛡️ **Admin** — Quản trị viên, duyệt sản phẩm, quản lý người dùng, điều chỉnh số dư ví.

---

## 🧰 2. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | **Java 21** (Virtual Threads) |
| Giao diện | **JavaFX 17** (FXML + MVC) |
| CSDL | **MySQL 8.0** |
| Connection Pool | **HikariCP 5.1.0** |
| JSON | **Gson 2.10.1** |
| Mã hoá mật khẩu | **BCrypt 0.10.2** |
| Unit Test | **JUnit 5 (5.10.2)** |
| Build tool | **Maven (multi-module)** |
| Giao tiếp | **TCP Socket** |
| Logging | SLF4J Simple |

### 📋 Yêu cầu môi trường
- **JDK 21+** (bắt buộc cho server, dùng Virtual Threads)
- **JavaFX SDK 17** (đã được Maven Shade đóng gói sẵn vào file `.jar` client)
- **MySQL 8.0+** (có thể dùng cloud — dự án mặc định dùng Aiven Cloud)
- **Maven 3.8+** (nếu build lại từ source)

---

## 🗂️ 3. Cấu trúc thư mục

```
Online-Auction-System-main/
├── pom.xml                            # Maven parent (multi-module)
├── Readme.md                          # File README này
├── out/                               # ⭐ Chứa các file .jar đã build sẵn
│   ├── auction-server-1.0-SNAPSHOT.jar
│   └── auction-client-1.0-SNAPSHOT.jar
│
├── auction-server/                    # 📦 Module Server
│   ├── pom.xml
│   └── src/main/java/server/
│       ├── application/MainServer.java      # Entry point — mở ServerSocket :8080
│       ├── dao/                              # Tầng DAO (Generic + Impl)
│       │   ├── core/ (DBConnection, GenericDAO)
│       │   ├── interfaces/ (UserDAO, ItemDAO, AuctionRoomDAO...)
│       │   └── impl/       (UserDAOImpl, ItemDAOImpl...)
│       ├── models/                           # Entity + Factory + Enum
│       │   ├── users/  (User, Admin, Seller, Bidder, UserFactory)
│       │   ├── items/  (Item, Art, Electronics, Vehicle, ItemFactory)
│       │   └── auction/(AuctionRoom, BidMessage, AutoBidConfig…)
│       ├── services/                         # Tầng nghiệp vụ
│       │   ├── AuctionService.java          # Facade + Singleton
│       │   ├── AuctionBidService.java       # Logic đặt giá
│       │   ├── AuctionAutoBidService.java   # Auto-bid đệ quy
│       │   ├── AuctionStatusService.java    # Scheduler đổi trạng thái
│       │   ├── AuctionSettlementService.java# Thanh toán ACID
│       │   ├── AuctionNotificationService.java
│       │   ├── WalletService.java
│       │   └── UserService.java, ItemService.java
│       ├── networks/                         # Tầng mạng
│       │   ├── ClientHandler.java           # Mỗi client = 1 virtual thread
│       │   ├── AuctionBroadcastManager.java # Observer Pattern
│       │   └── handlers/ (Auction/User/Item/AutoBid RequestHandler)
│       └── exceptions/                       # Custom exceptions
│   ├── src/main/resources/config.properties # ⚙️ Cấu hình DB
│   └── src/test/java/                        # JUnit 5 tests
│
└── auction-client/                    # 📦 Module Client (JavaFX)
    ├── pom.xml
    └── src/main/java/client/
        ├── ClientApp.java, Launcher.java
        ├── controllers/  (Login, Register, AuctionList, AuctionDetail,
        │                   SellerDashboard, AdminDashboard, AutoBidDialog…)
        ├── models/       (User, Item, AuctionViewModel, UserSession)
        ├── networks/     (ClientMain — kết nối Socket :8080)
        ├── services/     (ServerGateway, RequestResponse)
        └── utils/        (Formatter, Dialogs, StyledComponents…)
    └── src/main/resources/client/views/      # FXML views
```

---

## 📦 4. Vị trí các file `.jar`

> ⚠️ **GIỮ NGUYÊN VỊ TRÍ — KHÔNG DI CHUYỂN**

| File | Đường dẫn | Vai trò |
|---|---|---|
| **Server** | `out/auction-server-1.0-SNAPSHOT.jar` | Fat-jar Server (Shade-plugin, đã gồm HikariCP + MySQL Connector + Gson + BCrypt) |
| **Client** | `out/auction-client-1.0-SNAPSHOT.jar` | Fat-jar Client (Shade-plugin, đã gồm JavaFX Controls + FXML + Gson) |

---
## 🚀 5. Hướng dẫn chạy hệ thống
### Yêu cầu
- Máy tính đã cài đặt sẵn Java Runtime Environment (JRE) 21 trở lên.
- Java 21 (server), Java 17 (client)
- MySQL đang chạy, database `daugia`
- Maven 3.8+
- Cơ sở dữ liệu MySQL đang chạy, database daugia (đã cấu hình sẵn dữ liệu).
### Vận hành hệ thống
- Vui lòng mở 2 cửa sổ Terminal độc lập tại thư mục gốc của dự án (Online-Auction-System/) và khởi chạy hệ thống bằng dòng lệnh theo đúng thứ tự bắt buộc dưới đây:
### Server
```bash
java -jar out/auction-server-1.0-SNAPSHOT.jar
```

### Client
```bash
java -jar out/auction-client-1.0-SNAPSHOT.jar
```

---


## ✅ 6. Danh sách chức năng đã hoàn thành

### 👥 Quản lý người dùng
- [x] Đăng ký / Đăng nhập với mật khẩu mã hoá **BCrypt**
- [x] Phân quyền 3 vai trò: **Admin / Seller / Bidder**
- [x] Quản lý phiên đăng nhập (`UserSession`)
- [x] Admin điều chỉnh số dư ví người dùng

### 🛒 Quản lý sản phẩm & phiên đấu giá
- [x] CRUD sản phẩm (Seller đăng sản phẩm)
- [x] 3 loại sản phẩm: **Art / Electronics / Vehicle** (Factory Pattern)
- [x] Admin duyệt / từ chối sản phẩm
- [x] Vòng đời phiên: `OPEN → RUNNING → FINISHED → PAID / CANCELED`
- [x] Scheduler tự động chuyển trạng thái phiên (mỗi 1 giây)

### 💰 Đấu giá thời gian thực
- [x] Đặt giá thủ công với validate số dư ví
- [x] **Auto-Bidding**: Cấu hình giá tối đa + bước nhảy, server tự động đặt giá hộ (delay 2000ms)
- [x] **Anti-sniping**: Gia hạn phiên thêm 60s nếu có bid trong 30s cuối (tối đa 5 lần)
- [x] **Realtime Broadcast**: Mọi client trong phòng đều nhận cập nhật giá tức thời (Observer Pattern)
- [x] **Realtime Price Curve**: Biểu đồ `LineChart` JavaFX vẽ đường giá theo thời gian
- [x] Lịch sử bid hiển thị trong AuctionDetail

### 💳 Thanh toán & Ví điện tử
- [x] Transaction **ACID**: trừ ví người thắng + cộng ví người bán nguyên tử
- [x] Rollback khi lỗi
- [x] WalletService nạp / rút / xem số dư

### 🛡️ Concurrency & An toàn dữ liệu
- [x] **3 lớp bảo vệ**:
    - L1 — `synchronized(room)` tại Service Layer (JVM-level)
    - L2 — **Optimistic Locking** tại SQL (`WHERE version = ?`)
    - L3 — `ConcurrentHashMap` cho cache phòng đấu giá
- [x] **Virtual Threads** (Java 21) — mỗi client một thread, mở rộng hàng nghìn kết nối

### 🎨 Design Patterns áp dụng
- [x] **Singleton** — `AuctionService`, `DBConnection`
- [x] **Factory Method** — `UserFactory`, `ItemFactory`
- [x] **Observer** — `AuctionBroadcastManager`
- [x] **Facade** — `AuctionService` che chắn 5 sub-service
- [x] **DAO** — `GenericDAO<T>` + các DAO con

### 🧪 Unit Test (JUnit 5)
- [x] `AuctionRoomTest` — logic phòng đấu giá
- [x] `ItemFactoryTest` — Factory Pattern
- [x] `UserTest` — Entity người dùng
- [x] `ValidationTest` — kiểm tra ràng buộc input

---

## 📑 7. Tài liệu & Demo

- 📄 **Báo cáo PDF**: [Bao_Cao_BTL]()
- 🎬 **Video Demo**: [Video_Demo](https://youtu.be/AAIfJ4IhYmM?si=ySFTm3btiSTW2ReB)

---

## 👥 8. Phân công công việc

| STT | Thành viên | Phân công chính |
|:--:|---|---|
| 1 | Thành viên 1 | Model, Factory Pattern, Unit Test |
| 2 | Thành viên 2 | Service Layer, Concurrency, ACID Transaction |
| 3 | Thành viên 3 | Network, Observer, Auto-bid, Anti-sniping |
| 4 | Thành viên 4 | DAO, HikariCP, Client JavaFX (MVC, FXML) |


---

