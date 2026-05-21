# 🏷️ Hệ thống Đấu giá Trực tuyến — AuctionVN



Dự án Hệ thống Đấu giá Trực tuyến được phát triển theo mô hình **Client-Server**, áp dụng các nguyên lý **OOP**, **Design Patterns** và kiến trúc **MVC**. Giao tiếp qua **Socket + JSON (Gson)**. Giao diện sử dụng **JavaFX + FXML**.
![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-17-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![Maven](https://img.shields.io/badge/Maven-3.8-red)
## ✨ Key Highlights

- Realtime bidding using Socket + JSON
- Java 21 Virtual Threads for scalable concurrency
- Auto-bidding with recursive processing
- Anti-sniping mechanism for fair bidding
- JavaFX realtime visualization
- Optimistic locking for concurrent bid safety

---
## 👥 1. Phân công công việc
---

| Thành viên | Vai trò | Trách nhiệm chính |
| :--- | :--- | :--- |
| **Thành viên 1 (Leader)** | Backend Core | Thiết kế kiến trúc OOP, Design Patterns, thuật toán Auto-bid & Anti-sniping |
| **Thành viên 2** | Backend Business | CRUD sản phẩm, vòng đời phiên đấu giá (`OPEN→RUNNING→FINISHED→PAID/CANCELED`), Unit Test |
| **Thành viên 3** | Frontend JavaFX | Màn hình Login, AuctionList, AuctionDetail (realtime), SellerDashboard, biểu đồ giá LineChart |
| **Thành viên 4** | Server/DB/DevOps | DB Schema, Socket Server, Concurrency, CI/CD GitHub Actions |

---

## 📂 2. Cấu trúc thư mục thực tế

```text
Online-Auction-System/
├── .github/
│   └── workflows/
│       ├── ci.yml                  # GitHub Actions: build + test server, compile client
│       └── qodana_code_quality.yml # Qodana static analysis
├── auction-server/                 # Module Server (Java 21, Maven)
│   ├── pom.xml
│   └── src/
│       ├── server/
│       │   ├── application/
│       │   │   └── MainServer.java         # Entry point — Virtual Threads + scheduler
│       │   ├── networks/
│       │   │   ├── ClientHandler.java      # Xử lý từng client, Command Pattern
│       │   │   └── dto/
│       │   │       └── MessageDTO.java     # DTO gửi/nhận qua Socket (JSON)
│       │   ├── services/
│       │   │   ├── AuctionService.java     # Singleton — orchestrate bid, auto-bid, status
│       │   │   ├── UserService.java
│       │   │   └── PasswordUtil.java       # BCrypt hash/verify
│       │   ├── dao/
│       │   │   ├── core/
│       │   │   │   ├── DBConnection.java   # Singleton HikariCP connection pool
│       │   │   │   └── GenericDAO.java
│       │   │   ├── interfaces/
│       │   │   │   ├── AuctionRoomDAO.java
│       │   │   │   ├── AutoBidDAO.java
│       │   │   │   ├── BidMessageDAO.java
│       │   │   │   ├── ItemDAO.java
│       │   │   │   └── UserDAO.java
│       │   │   └── impl/
│       │   │       ├── AuctionRoomDAOImpl.java   # Optimistic locking
│       │   │       ├── AutoBidDAOImpl.java
│       │   │       ├── BidMessageDAOImpl.java
│       │   │       ├── ItemDAOImpl.java
│       │   │       └── UserDAOImpl.java
│       │   ├── models/
│       │   │   ├── auction/
│       │   │   │   ├── AuctionRoom.java    # placeBid, placeAutoBid, Anti-sniping
│       │   │   │   ├── AuctionStatus.java  # Enum: OPEN, RUNNING, FINISHED, PAID, CANCELED
│       │   │   │   ├── AutoBidConfig.java
│       │   │   │   └── BidMessage.java
│       │   │   ├── items/
│       │   │   │   ├── Item.java (abstract), Art.java, Electronics.java, Vehicle.java
│       │   │   │   ├── ItemCategory.java, ItemFactory.java
│       │   │   └── users/
│       │   │       ├── User.java (abstract), Admin.java, Bidder.java, Seller.java
│       │   │       ├── UserRole.java, UserFactory.java, CreateAdmin.java
│       │   ├── exceptions/
│       │   │   ├── DAOException.java, DatabaseConnectionException.java
│       │   │   ├── DuplicateDataException.java, InvalidBidException.java
│       │   │   └── NotFoundException.java
│       │   └── utils/
│       │       └── Validation.java         # Validate email, password, bid, state transition
│       └── test/java/
│           ├── AuctionRoomTest.java        # 10 tests: bid, anti-snipe, auto-bid, tie-breaker
│           ├── AuctionServiceTest.java     # autoUpdateStatuses
│           ├── UserTest.java               # updateBalance
│           └── ValidationTest.java        # validate email, bid, payment, transition
│
└── auction-client/                 # Module Client (Java 17, Maven + JavaFX 17)
    ├── pom.xml
    └── src/client/
        ├── ClientApp.java          # JavaFX Application entry point
        ├── Launcher.java           # Wrapper tránh lỗi classpath JavaFX
        ├── controllers/
        │   ├── LoginController.java
        │   ├── RegisterController.java
        │   ├── AuctionListController.java
        │   ├── AuctionDetailController.java  # Realtime + LineChart
        │   ├── SellerDashboardController.java
        │   ├── AddProductController.java
        │   └── AdminDashboardController.java
        ├── models/
        │   ├── auction/AuctionViewModel.java
        │   ├── item/  Item.java, Art.java, Electronics.java
        │   └── user/  User.java, Seller.java, UserSession.java, UserViewModel.java
        ├── networks/
        │   ├── ClientMain.java     # Singleton Socket — Observer listener (action→callback)
        │   └── MessageDTO.java
        └── views/
            ├── login.fxml, register.fxml
            ├── auction-list.fxml, auction-detail.fxml
            ├── seller-dashboard.fxml, add-product-dialog.fxml
            ├── admin-dashboard.fxml
            └── app.css
```

---

## 🏗️ 3. Kiến trúc hệ thống

```
[Client JavaFX]  ←── Socket + JSON ──→  [Server MainServer]
     │                                          │
 Controllers                            ClientHandler
 (FXML + @FXML)                         (Command Pattern:
     │                                   Map<action, processor>)
 ClientMain                                     │
 (Observer:                             AuctionService
  action→callback)                      (Singleton)
                                                │
                                      DAO Layer (HikariCP)
                                                │
                                           MySQL DB
```

**Luồng Bid thủ công:**
1. Client gửi `{"action":"BID","payload":"roomId:username:amount"}`
2. `ClientHandler.handleBid()` → `AuctionService.handleBidRequest()`
3. `synchronized(room)` → `room.placeBid()` (validate + Anti-sniping)
4. Async: `roomDAO.update(room, oldPrice)` (Optimistic Lock) + `bidDAO.insert()`
5. `processAutoBids()` → bot tự trả giá đệ quy nếu có config
6. `ClientHandler.broadcast("UPDATE_PRICE", ...)` → tất cả client cập nhật realtime

---

## 🎨 4. Design Patterns áp dụng

| Pattern | Vị trí | Mô tả |
| :--- | :--- | :--- |
| **Singleton** | `AuctionService`, `DBConnection` | 1 instance duy nhất toàn server |
| **Factory Method** | `ItemFactory`, `UserFactory` | Tạo đúng subclass theo category/role |
| **Observer** | `ClientMain` + `ClientHandler.broadcast()` | Realtime update không polling |
| **Command** | `Map<String, RequestProcessor>` trong `ClientHandler` | Mỗi action → 1 handler method |

---

## ⚙️ 5. Tính năng nâng cao

### Auto-Bidding
User thiết lập `maxBid` + `increment`. Khi có bid mới, `AuctionService.processAutoBids()` tự động trả giá thay user, xử lý ALL-IN khi bước giá vượt trần, tie-breaker theo thứ tự đăng ký.

### Anti-Sniping
Bid trong 30 giây cuối → gia hạn thêm 60 giây tính từ thời điểm bid (đảm bảo luôn còn ≥60s). Tối đa 5 lần gia hạn.

### Concurrent Bidding Safety
- `synchronized(room)` + **Optimistic Locking** DB (`WHERE current_highest_price = ?`)
- `ConcurrentHashMap<Long, AuctionRoom>` + `CopyOnWriteArrayList<ClientHandler>`
- **Java 21 Virtual Threads** — mỗi client 1 thread cực nhẹ

### Bid History Visualization
`LineChart<String, Number>` cập nhật realtime mỗi `UPDATE_PRICE` nhận được — không cần refresh.

---

## 🚀 6. Hướng dẫn chạy

### Yêu cầu
- Java 21 (server), Java 17 (client)
- MySQL đang chạy, database `daugia`
- Maven 3.8+

### Server
```bash
cd auction-server
mvn clean package -DskipTests
java -jar target/auction-server-1.0-SNAPSHOT-shaded.jar
```

### Client
```bash
cd auction-client
mvn javafx:run
```

---

## 🧪 7. Unit Test

```bash
cd auction-server
mvn test
```

| File | Số test | Nội dung |
| :--- | :--- | :--- |
| `AuctionRoomTest` | 10 | Bid hợp lệ/không hợp lệ, Anti-sniping, Auto-bid war, Tie-breaker |
| `AuctionServiceTest` | 1 | autoUpdateStatuses: OPEN→RUNNING, RUNNING→CANCELED |
| `UserTest` | 3 | updateBalance nạp/trừ/không đủ số dư |
| `ValidationTest` | 4 | Email, bid amount, payment ability, state transition |

---

## 🔧 8. CI/CD

GitHub Actions (`.github/workflows/ci.yml`) tự động trên mỗi push/PR vào `main`:

1. **build-server** — `mvn clean verify`: build + chạy JUnit, upload surefire report, đóng gói JAR
2. **build-client** — `mvn clean compile`: kiểm tra client compile thành công
3. **ci-success** — gate tổng hợp: chỉ green khi cả 2 job trên pass

**Qodana** (`qodana_code_quality.yml`) phân tích chất lượng code tĩnh mỗi PR, post comment kết quả.