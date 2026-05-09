# BÁO CÁO PHÂN TÍCH BACKEND
## Hệ thống Đấu giá Trực tuyến — AuctionVN

---

**Mục tiêu báo cáo:** Trình bày chi tiết những gì đã được hiện thực ở phần backend, vai trò của từng thành phần, lý do lựa chọn công nghệ/kỹ thuật và đối chiếu với chín chủ đề lý thuyết đã học (kế thừa & đa hình, lớp trừu tượng — lập trình tổng quát — giao diện, cấu trúc dữ liệu trong Java, xử lý ngoại lệ, mẫu thiết kế, đa luồng, tái cấu trúc mã nguồn, kiểm thử, tích hợp & triển khai).

---

## 1. Tổng quan kiến trúc backend

Backend được tổ chức thành một module Maven độc lập (`auction-server`), chạy trên **Java 21**, giao tiếp với client qua **TCP Socket** truyền chuỗi JSON (`Gson`), và lưu trữ dữ liệu bền vững vào **MySQL** thông qua **HikariCP connection pool**.

Mã nguồn được phân tầng rõ ràng theo nguyên tắc *separation of concerns*:

```text
application/   →  Điểm vào (MainServer)
networks/      →  Tầng giao tiếp Socket (ClientHandler, MessageDTO)
services/      →  Tầng nghiệp vụ (AuctionService, UserService, PasswordUtil)
dao/           →  Tầng truy cập dữ liệu (interfaces + impl + GenericDAO)
models/        →  Tầng domain (User, Item, AuctionRoom, BidMessage, AutoBidConfig)
exception/     →  Hệ thống ngoại lệ tự định nghĩa
utils/         →  Tiện ích dùng chung (Validation)
test/          →  Bộ kiểm thử JUnit 5
```

Cách phân tầng này tương ứng với kiến trúc 3-layer (Presentation–Service–Data Access). Việc tách rõ ràng giúp mỗi tầng chỉ phụ thuộc vào tầng dưới qua *interface*, dễ thay thế triển khai (ví dụ đổi từ MySQL sang PostgreSQL chỉ cần viết lại tầng `dao/impl`), và đặc biệt phù hợp cho việc viết unit test cho từng tầng riêng biệt.

**Luồng xử lý một yêu cầu điển hình:**

```text
Client ──JSON──▶ ServerSocket ──▶ Virtual Thread ──▶ ClientHandler
                                                          │
                                                          ▼
                                          processors.get(action).process()
                                                          │
                                                          ▼
                                                  AuctionService
                                              (synchronized + CompletableFuture)
                                                          │
                                                          ▼
                                                       DAO Layer
                                                  (HikariCP Pool)
                                                          │
                                                          ▼
                                                       MySQL
```

---

## 2. Phân tích chi tiết từng thành phần

### 2.1. Tầng Application — `MainServer.java`

`MainServer` là điểm vào của server. Hai nhiệm vụ:

1. **Lắng nghe kết nối** ở cổng 8080 và sinh ra một **Virtual Thread** cho mỗi client kết nối tới.
2. Khởi động một **`ScheduledExecutorService`** chạy nền, mỗi giây quét trạng thái tất cả phòng đấu giá để tự động chuyển `OPEN→RUNNING→FINISHED`.

```text
public void start() {
    while (isRunning) {
        Socket clientSocket = serverSocket.accept();
        Thread.startVirtualThread(new ClientHandler(clientSocket));   // Java 21
    }
}
```

**Tại sao Virtual Thread (Java 21) thay vì `new Thread()` hay thread pool truyền thống?**

| Tiêu chí                    | Platform Thread (`new Thread`) | Fixed Thread Pool        | Virtual Thread |
|:----------------------------|:-------------------------------|:-------------------------|:---------------|
| Chi phí RAM/luồng           | ~1 MB stack                    | ~1 MB × poolSize         | ~vài KB        |
| Số luồng đồng thời thực tế  | vài nghìn                      | bị giới hạn poolSize     | hàng triệu     |
| Phù hợp tác vụ I/O-bound    | Tốn tài nguyên                 | Có thể nghẽn khi I/O lâu | Tối ưu nhất    |

Server đấu giá là tác vụ **I/O-bound điển hình** (chờ socket, chờ DB), nên Virtual Thread cho phép phục vụ rất nhiều client đồng thời mà không cần lo cấu hình kích thước pool. Đây là lý do được ghi chú rõ trong code:

```text
// TỐI ƯU: Sử dụng Virtual Thread để xử lý ClientHandler
// Cực nhẹ, tốc độ xử lý nhanh và không làm nghẽn Server
Thread.startVirtualThread(new ClientHandler(clientSocket));
```

`Runtime.addShutdownHook` được dùng để đảm bảo khi server tắt (Ctrl+C, hoặc `kill`), scheduler dừng và pool DB đóng sạch sẽ — tránh rò rỉ tài nguyên.

**Liên hệ kiến thức đã học:** *Đa luồng* (Virtual Thread, ScheduledExecutorService, shutdown hook).

---

### 2.2. Tầng Networks — `ClientHandler` và `MessageDTO`

Mỗi `ClientHandler` triển khai interface `Runnable`, đại diện cho một phiên kết nối với một client.

**Giao thức:** Client gửi một dòng JSON theo cấu trúc `MessageDTO { action, payload }`. Server đọc, dispatch tới handler tương ứng, rồi gửi trả `MessageDTO` phản hồi.

```text
public class MessageDTO {
    private String action;   // "LOGIN", "BID", "ADD_ITEM", ...
    private String payload;  // chuỗi dữ liệu
}
```

**Cơ chế dispatch — Command Pattern bằng `Map<String, RequestProcessor>`:**

```text
@FunctionalInterface
interface RequestProcessor { MessageDTO process(MessageDTO request); }

private void initProcessors() {
    processors.put("LOGIN",       this::handleLogin);
    processors.put("BID",         this::handleBid);
    processors.put("ADD_ITEM",    this::handleAddItem);
    // ... 20 lệnh
}
```

Trong hàm `run()`:

```text
RequestProcessor processor = processors.getOrDefault(request.getAction(),
        (req) -> new MessageDTO("ERROR", "Hành động không hợp lệ"));
MessageDTO response = processor.process(request);
```

**Tại sao chọn Command Pattern thay vì `if-else`/`switch-case` dài?**

- Khi cần thêm action mới, chỉ thêm 1 dòng `processors.put(...)` thay vì sửa khối `switch` đang có. Đây chính là **Open/Closed Principle**.
- Mỗi handler là một method nhỏ, dễ test riêng lẻ.
- Tránh phương thức `run()` phình to thành "God method" hàng trăm dòng.

**Broadcast giá realtime:**

```text
private static final CopyOnWriteArrayList<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

public static void broadcast(String json) {
    activeClients.forEach(c -> { if (c.out != null) c.out.println(json); });
}
```

`CopyOnWriteArrayList` được chọn thay cho `ArrayList` vì danh sách client liên tục bị **đọc/duyệt** (broadcast) trong khi vẫn có thể có client mới **thêm/bớt**. Cấu trúc này tối ưu cho tỷ lệ đọc nhiều — ghi ít, không bị `ConcurrentModificationException` mà không cần `synchronized` thủ công.

**Role guard — chống truy cập trái phép:**

```text
private MessageDTO requireSeller() {
    if (loggedInUser == null) return new MessageDTO("ERROR", "Chưa đăng nhập");
    if (!(loggedInUser instanceof Seller))
        return new MessageDTO("ERROR", "Chỉ Seller mới được thực hiện hành động này!");
    return null;
}
```

Đây là kỹ thuật **kiểm tra kiểu thực sự (RTTI) bằng `instanceof`** — một biểu hiện trực tiếp của *đa hình tham chiếu* (cùng biến `User loggedInUser` có thể giữ `Bidder`, `Seller`, hoặc `Admin`).

**Liên hệ kiến thức đã học:**
- *Mẫu thiết kế:* Command Pattern (Map ↔ FunctionalInterface).
- *Cấu trúc dữ liệu:* `HashMap`, `CopyOnWriteArrayList`, `LinkedHashMap` (giữ thứ tự khi serialize JSON).
- *Đa hình:* `instanceof Seller`, `(Bidder) loggedInUser`.

---

### 2.3. Tầng Models — Domain Layer

#### a) Cây kế thừa `User`

```text
User (abstract)
 ├── Bidder    (reputationScore, canPlaceBid)
 ├── Seller    (sellerRating, totalItemsSold, incrementItemsSold)
 └── Admin     (accessLevel, department, canManageUsers)
```

`User` được khai báo `abstract` cùng phương thức `abstract String getRole()`, buộc mọi lớp con phải tự định nghĩa vai trò:

```text
public abstract class User {
    protected BigDecimal accountBalance;

    public boolean updateBalance(BigDecimal amount) {        // logic dùng chung
        BigDecimal newBalance = this.accountBalance.add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) >= 0) {
            this.accountBalance = newBalance;
            return true;
        }
        return false;
    }

    public abstract String getRole();                        // bắt buộc override
}
```

Phương thức `updateBalance()` thể hiện rõ giá trị của kế thừa: logic kiểm tra số dư âm chỉ viết **một lần** ở lớp cha, cả `Bidder` (khi đặt giá) và `Seller` (khi nhận thanh toán) đều dùng được.

**Lưu ý kỹ thuật:** `accountBalance` dùng **`BigDecimal`** thay vì `double`. Lý do: `double` có sai số nhị phân (`0.1 + 0.2 != 0.3`), không bao giờ được dùng cho tiền tệ. `BigDecimal` cho phép cộng trừ chính xác tới đơn vị nhỏ nhất.

#### b) Cây kế thừa `Item`

```text
Item (abstract, implements Serializable)
 ├── Art          → getCategoryInfo() = "ART"
 ├── Electronics  → getCategoryInfo() = "ELECTRONIC"
 └── Vehicle      → getCategoryInfo() = "VEHICLE" (có thêm brand, year, engine)
```

Tương tự `User`, `Item` ép các lớp con triển khai `getCategoryInfo()`. Khi cần thêm danh mục mới (ví dụ `RealEstate`), chỉ cần tạo lớp con — không sửa code cũ.

#### c) `AuctionRoom` — trung tâm nghiệp vụ

`AuctionRoom` là class quan trọng nhất, chứa toàn bộ logic của một phiên đấu giá. Ba điểm đáng chú ý:

**(1) Trạng thái dùng `enum` thay vì `String`/`int`:**

```text
public enum AuctionStatus { OPEN, RUNNING, FINISHED, PAID, CANCELED }
```

Compiler bắt lỗi gõ sai (`"FINSHED"` không compile), `switch` trên enum vét cạn được mọi nhánh, và `enum` là kiểu type-safe hoàn toàn — vượt trội hơn so với việc dùng các hằng `String` rời rạc.

**(2) Anti-Sniping (chống bắn tỉa giây cuối):**

```text
private static final int ANTI_SNIPE_THRESHOLD_SECONDS = 30;
private static final int ANTI_SNIPE_EXTENSION_SECONDS = 60;
private static final int MAX_EXTENSIONS = 5;

private void triggerAntiSniping() {
    if (extensionCount >= MAX_EXTENSIONS) return;
    LocalDateTime now = LocalDateTime.now();
    if (!now.isAfter(this.endTime)
            && !this.endTime.isAfter(now.plusSeconds(ANTI_SNIPE_THRESHOLD_SECONDS))) {
        this.endTime = now.plusSeconds(ANTI_SNIPE_EXTENSION_SECONDS);
        extensionCount++;
    }
}
```

Khi có người đặt giá trong **30 giây cuối** của phiên, thời gian kết thúc tự động được kéo dài thêm 60 giây (tối đa 5 lần). Đây là cơ chế eBay đã áp dụng từ năm 2002 để ngăn chặn việc người chơi rình bid ở giây cuối khiến đối thủ không kịp phản ứng.

**(3) Đồng bộ hóa cấp instance:**

```text
public synchronized void placeBid(Bidder bidder, BigDecimal amount) throws InvalidBidException {
    // validate + cập nhật state
}
```

Từ khóa `synchronized` đảm bảo chỉ một thread tại một thời điểm có thể sửa state của cùng một phòng — chống **race condition** khi hai bidder bid đồng thời.

#### d) `AutoBidConfig` triển khai `Comparable<AutoBidConfig>`

```text
public int compareTo(AutoBidConfig other) {
    return this.registerTime.compareTo(other.registerTime);
}
```

Cho phép `Collections.sort(autoBids)` sắp xếp theo thời gian đăng ký để áp dụng luật **tie-breaker**: nếu hai người cùng đặt mức `maxBid` bằng nhau, người đăng ký auto-bid trước sẽ thắng.

**Liên hệ kiến thức đã học:**
- *Lớp trừu tượng:* `User`, `Item`.
- *Kế thừa & Đa hình:* `Bidder/Seller/Admin extends User`; `Art/Electronics/Vehicle extends Item`; `@Override getRole()`/`getCategoryInfo()`.
- *Giao diện:* `Serializable`, `Comparable<AutoBidConfig>`.
- *Cấu trúc dữ liệu:* `enum AuctionStatus`, `List<BidMessage>`.

---

### 2.4. Tầng DAO — Truy cập dữ liệu

#### a) `GenericDAO<T>` — Lập trình tổng quát (Generics)

```text
public interface GenericDAO<T> {
    void insert(T obj) throws Exception;
    void update(T obj) throws Exception;
    void delete(int id) throws Exception;
    T findById(int id) throws Exception;
    List<T> findAll() throws Exception;
}
```

Tất cả 5 DAO khác đều `extends GenericDAO<...>` với type parameter cụ thể:

```text
public interface UserDAO        extends GenericDAO<User>        { ... }
public interface ItemDAO        extends GenericDAO<Item>        { ... }
public interface AuctionRoomDAO extends GenericDAO<AuctionRoom> { ... }
public interface BidMessageDAO  extends GenericDAO<BidMessage>  { ... }
```

**Tại sao Generic mà không tự viết 5 interface riêng?**

- Đảm bảo type safety: gọi `userDAO.findById(1)` luôn trả về `User`, không cần ép kiểu thủ công.
- DRY (Don't Repeat Yourself): hợp đồng CRUD chung được khai báo một lần.
- Mỗi DAO con chỉ cần khai báo *thêm* các phương thức đặc thù (vd: `findBySellerId`, `findByUsername`).

#### b) `DBConnection` — Singleton + HikariCP Pool

```text
private static volatile HikariDataSource dataSource;

public static HikariDataSource getDataSource() {
    if (dataSource == null) {
        synchronized (DBConnection.class) {
            if (dataSource == null) {                       // double-checked locking
                HikariConfig config = new HikariConfig();
                config.setMaximumPoolSize(20);
                config.setMinimumIdle(5);
                // ...
                dataSource = new HikariDataSource(config);
            }
        }
    }
    return dataSource;
}
```

**Tại sao HikariCP thay vì một `Connection` dùng chung (cách viết phổ biến của sinh viên)?**

| Vấn đề                 | Single shared `Connection`            | HikariCP Pool                   |
|:-----------------------|:--------------------------------------|:--------------------------------|
| Đa luồng               | Cursor state bị corrupt → kết quả sai | Mỗi thread một connection riêng |
| Lỗi `ResultSet closed` | Thường xuyên xảy ra                   | Không xảy ra                    |
| Hiệu năng              | Là bottleneck (chỉ 1 lệnh chạy 1 lúc) | 20 lệnh song song               |
| Ngắt mạng              | Connection chết, không tự khôi phục   | Tự test & cấp lại từ pool       |

Kết hợp với `try-with-resources`:

```text
try (Connection conn = DBConnection.getInstance();
     PreparedStatement pstmt = conn.prepareStatement(sql)) {
    // ...
} // conn tự đóng & trả về pool
```

**Optimistic Locking (CAS) trong `AuctionRoomDAOImpl.update()`:**

```text
String sql = "UPDATE auctions SET current_highest_price = ?, ... " +
             "WHERE auction_id = ? AND current_highest_price = ?";
// ...
int rowsAffected = pstmt.executeUpdate();
if (rowsAffected == 0) {
    throw new Exception("Xung đột dữ liệu (Lost Update): Đã có người khác đặt giá cao hơn!");
}
```

Câu lệnh chỉ update nếu giá hiện tại trong DB **vẫn đúng bằng** `oldPrice`. Nếu trong khoảng thời gian giữa lúc đọc và lúc ghi, có thread khác đã update — `rowsAffected = 0` và ta biết được. Đây là cơ chế chống **lost update** kinh điển ở mức tầng DB, nhanh hơn việc khoá hàng (`SELECT ... FOR UPDATE`) vì không chặn các thread khác.

**Bảo mật SQL Injection:** Mọi câu lệnh đều dùng `PreparedStatement` với placeholder `?`, không bao giờ nối chuỗi.

**Transaction nguyên tử trong `transferMoney`:**

```text
conn.setAutoCommit(false);
try {
    // 1. Trừ tiền người mua  (UPDATE ... WHERE balance >= ?)
    if (rowsAffectedWithdraw == 0) { conn.rollback(); return false; }
    // 2. Cộng tiền người bán
    conn.commit();
} catch (Exception ex) {
    conn.rollback();    // an toàn — không rớt tiền giữa chừng
}
```

**Liên hệ kiến thức đã học:**
- *Lập trình tổng quát:* `GenericDAO<T>`.
- *Mẫu thiết kế:* Singleton (DBConnection), DAO Pattern.
- *Đa luồng:* `volatile`, double-checked locking, connection pool.
- *Xử lý ngoại lệ:* `try-with-resources`, transaction rollback.

---

### 2.5. Tầng Services

#### a) `AuctionService` — Singleton điều phối nghiệp vụ

```text
public static synchronized AuctionService getInstance() {
    if (instance == null) instance = new AuctionService();
    return instance;
}

private ConcurrentHashMap<Long, AuctionRoom> activeRooms;
```

Toàn bộ phòng đấu giá đang hoạt động được giữ trong RAM bằng `ConcurrentHashMap` để tra cứu O(1) và an toàn đa luồng. Đây là tối ưu hóa quan trọng: bid là thao tác cần phản hồi dưới 100 ms, không thể mỗi lần bid lại đi query DB.

**Phân chia trách nhiệm in-memory ↔ persistent:**

```text
synchronized (room) {
    room.placeBid(bidder, bidAmount);        // cập nhật RAM trước (nhanh)
}

CompletableFuture.runAsync(() -> {           // ghi DB bất đồng bộ
    roomDAO.update(room, oldPrice);
    bidDAO.insert(bid);
    processAutoBids(room, bidder);
});
```

**Tại sao tách thành hai bước và dùng `CompletableFuture`?**

- Người dùng nhìn thấy giá mới ngay lập tức (vì đã update RAM).
- I/O xuống DB chạy nền — không chặn thread phục vụ client.
- Nếu DB lỗi, có log riêng để retry; UI không bị treo.

**Auto-bid recursive:**

```text
private void processAutoBids(AuctionRoom room, Bidder lastBidder) {
    List<AutoBidConfig> autoBids = autoBidDAO.getAutoBidsByAuctionId(room.getId());
    for (AutoBidConfig config : autoBids) {
        if (autoBidder.getUserId() == lastBidder.getUserId()) continue;
        if (room.getCurrentWinnerId() == autoBidder.getUserId()) continue;

        BigDecimal nextBid = room.getCurrentPrice().add(config.getIncrement());
        if (nextBid.compareTo(config.getMaxBid()) > 0) {
            // gửi cảnh báo "AUTO_BID_EXCEEDED" về client
            continue;
        }
        // ghi DB + broadcast
        processAutoBids(room, fullBidder);   // đệ quy cho người tiếp theo
        break;
    }
}
```

**Settlement logic** (`processAuctionSettlement`): khi phiên kết thúc, hệ thống tự kiểm tra số dư người thắng, trừ tiền người mua, cộng tiền người bán, chuyển trạng thái sang `PAID` hoặc `CANCELED`. Đây là logic giao dịch tài chính kinh điển.

#### b) `PasswordUtil` — Hash mật khẩu bằng BCrypt

```text
public static String hash(String password) {
    return BCrypt.withDefaults().hashToString(12, password.toCharArray());
}
public static boolean verify(String password, String hash) {
    return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
}
```

**Tại sao BCrypt thay vì MD5/SHA-1?**

- BCrypt có **salt** tự động, hai mật khẩu giống nhau cho hash khác nhau.
- BCrypt cố tình **chậm** (cost factor 12 = 2^12 vòng lặp), khiến brute-force tốn kém. MD5/SHA-1 quá nhanh, GPU có thể thử hàng tỷ mật khẩu/giây.
- DB bị lộ thì hacker vẫn không reverse-engineer được mật khẩu gốc.

**Liên hệ kiến thức đã học:**
- *Mẫu thiết kế:* Singleton (AuctionService).
- *Đa luồng:* `ConcurrentHashMap`, `synchronized (room)`, `CompletableFuture.runAsync()`.

---

### 2.6. Tầng Exceptions — Custom Exception Hierarchy

```text
Exception
 ├── DAOException                    // lỗi insert/update/delete chung
 ├── DuplicateDataException          // username trùng
 ├── InvalidBidException             // bid sai luật
 └── NotFoundException               // không tìm thấy bản ghi

RuntimeException
 └── DatabaseConnectionException     // không kết nối được DB
```

Việc tự định nghĩa exception thay vì throw `Exception` chung mang lại nhiều lợi ích:

```text
catch (DuplicateDataException e) {
    return new MessageDTO("REGISTER_FAILED", "Tên đăng nhập đã tồn tại!");
} catch (IllegalArgumentException e) {
    return new MessageDTO("REGISTER_FAILED", e.getMessage());
} catch (Exception e) {
    return new MessageDTO("REGISTER_FAILED", "Lỗi đăng ký: " + e.getMessage());
}
```

Mỗi loại lỗi có thông điệp khác nhau cho người dùng, thay vì hiện một dòng `Exception` chung chung. Đây là *checked vs unchecked exception* được phân loại đúng:
- `DatabaseConnectionException extends RuntimeException` — lỗi hệ thống không thể phục hồi tại chỗ.
- Các exception còn lại `extends Exception` — buộc tầng gọi phải xử lý.

**Liên hệ kiến thức đã học:** *Xử lý ngoại lệ*: tự định nghĩa exception, phân biệt checked/unchecked, multi-catch.

---

### 2.7. Tầng Utils — `Validation`

Tập hợp các hàm validate dữ liệu đầu vào:

```text
public static void validateUsername(String username)         // không rỗng, không khoảng trắng
public static void validatePassword(String password)         // ≥ 6 ký tự
public static void validateEmail(String email)               // regex
public static BigDecimal validateBidAmount(String amountStr) // > 0, đúng định dạng
public static boolean canTransitionTo(AuctionStatus current, AuctionStatus next)
```

`canTransitionTo` đặc biệt thú vị — đây là hiện thực của một **state machine**:

```text
case OPEN:     return next == RUNNING || next == CANCELED;
case RUNNING:  return next == FINISHED;
case FINISHED: return next == PAID || next == CANCELED;
```

Nó chặn các chuyển trạng thái sai logic (ví dụ: nhảy thẳng từ `OPEN` sang `PAID` mà bỏ qua giai đoạn đấu giá).

---

## 3. Tổng hợp các Mẫu thiết kế đã áp dụng

| # | Mẫu thiết kế          | Vị trí áp dụng                                                             | Vấn đề giải quyết                                                  |
|:-:|:----------------------|:---------------------------------------------------------------------------|:-------------------------------------------------------------------|
| 1 | **Singleton**         | `AuctionService.getInstance()`, `DBConnection.getDataSource()`             | Đảm bảo duy nhất một instance quản lý phòng đấu giá / pool kết nối |
| 2 | **Factory**           | `UserFactory.createUser(role,...)`, `ItemFactory.createItem(category,...)` | Tách logic khởi tạo polymorphic objects khỏi nơi sử dụng           |
| 3 | **DAO**               | Toàn bộ tầng `dao/`                                                        | Tách logic truy cập DB ra khỏi nghiệp vụ                           |
| 4 | **DTO**               | `MessageDTO`, `BidMessage`                                                 | Đóng gói dữ liệu để truyền qua mạng                                |
| 5 | **Command**           | `Map<String, RequestProcessor>` trong `ClientHandler`                      | Dispatch action mà không cần `switch-case` dài                     |
| 6 | **Strategy (ngầm)**   | `GenericDAO<T>` + các impl khác nhau                                       | Cùng một hợp đồng CRUD nhưng cách triển khai khác cho mỗi entity   |

---

## 4. Đối chiếu với 9 chủ đề kiến thức đã học

### 4.1. Thừa kế và đa hình

| Biểu hiện               | Vị trí trong code                                                          |
|:------------------------|:---------------------------------------------------------------------------|
| Kế thừa lớp             | `Bidder/Seller/Admin extends User`; `Art/Electronics/Vehicle extends Item` |
| Override (đa hình động) | `getRole()`, `getCategoryInfo()`                                           |
| Đa hình tham chiếu      | `User loggedInUser = ...; if (loggedInUser instanceof Seller) ...`         |
| Cast xuống              | `(Bidder) loggedInUser`, `(Seller) loggedInUser`                           |
| Gọi `super(...)`        | Constructor `Bidder(...)` gọi `super(userId, username, ...)`               |

### 4.2. Lớp trừu tượng, lập trình tổng quát, giao diện

| Biểu hiện             | Vị trí                                                                                                                              |
|:----------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| `abstract class`      | `User`, `Item`                                                                                                                      |
| `abstract method`     | `getRole()`, `getCategoryInfo()`                                                                                                    |
| Giao diện (interface) | `UserDAO`, `ItemDAO`, `AuctionRoomDAO`, `BidMessageDAO`, `AutoBidDAO`, `RequestProcessor`, `Comparable`, `Serializable`, `Runnable` |
| Functional Interface  | `RequestProcessor` (`@FunctionalInterface`) — kết hợp với method reference `this::handleLogin`                                      |
| Generics              | `GenericDAO<T>`, `Comparable<AutoBidConfig>`, `List<BidMessage>`, `ConcurrentHashMap<Long, AuctionRoom>`                            |

### 4.3. Cấu trúc dữ liệu trong Java

| Cấu trúc                                         | Vị trí                       | Lý do                                   |
|:-------------------------------------------------|:-----------------------------|:----------------------------------------|
| `ArrayList<BidMessage>`                          | `bidHistory` trong `AuctionRoom` | List động, truy cập tuần tự nhanh       |
| `HashMap<String, RequestProcessor>`              | `ClientHandler.processors`   | Tra cứu O(1) theo action                |
| `LinkedHashMap`                                  | Build payload JSON           | Giữ nguyên thứ tự field khi serialize   |
| `ConcurrentHashMap<Long, AuctionRoom>`           | `AuctionService.activeRooms` | Thread-safe, không cần khóa toàn bộ map |
| `CopyOnWriteArrayList<ClientHandler>`            | `activeClients`              | Tối ưu khi đọc nhiều — ghi ít           |
| `enum AuctionStatus`, `UserRole`, `ItemCategory` | Đại diện trạng thái cố định  | Type-safe, không gõ sai                 |

### 4.4. Xử lý ngoại lệ

- **Tự định nghĩa 5 exception** (`DAOException`, `DuplicateDataException`, `InvalidBidException`, `NotFoundException`, `DatabaseConnectionException`).
- Phân biệt **checked** (`extends Exception`) và **unchecked** (`extends RuntimeException`).
- **`try-with-resources`** áp dụng cho mọi `Connection`, `PreparedStatement`, `ResultSet` để tránh rò rỉ.
- **Multi-catch theo thứ tự ưu tiên** (catch cụ thể trước, `Exception` chung sau).
- **Try-catch trong scheduler** để một lần lỗi không làm chết toàn bộ luồng quét.

### 4.5. Mẫu thiết kế

Đã liệt kê chi tiết ở mục 3. Sáu mẫu được dùng có chủ đích, không phải áp dụng cho có.

### 4.6. Đa luồng

| Cơ chế                                  | Vị trí                                                            |
|:----------------------------------------|:------------------------------------------------------------------|
| Virtual Thread                          | `Thread.startVirtualThread(...)` cho mỗi client                   |
| `ScheduledExecutorService`              | Quét trạng thái phòng định kỳ 1 giây                              |
| `CompletableFuture.runAsync()`          | Ghi DB bất đồng bộ sau khi update RAM                             |
| `synchronized (room)`                   | Bảo vệ state phòng khi nhiều thread cùng bid                      |
| `synchronized` method                   | `AuctionRoom.placeBid()`, `placeAutoBid()`, `registerAutoBid()`   |
| `volatile` + double-checked locking     | `DBConnection.dataSource`                                         |
| Concurrent collections                  | `ConcurrentHashMap`, `CopyOnWriteArrayList`                       |
| Optimistic locking ở DB                 | `WHERE current_highest_price = oldPrice`                          |
| Connection pool                         | HikariCP — mỗi thread một connection độc lập                      |
| Shutdown hook                           | Đóng pool và scheduler khi server tắt                             |

### 4.7. Tái cấu trúc mã nguồn (Refactoring)

Có nhiều dấu vết refactoring rõ ràng trong code:

1. **Tách interface khỏi impl:** `UserDAO ↔ UserDAOImpl`, ban đầu có thể chỉ có `UserDAO` chứa cả logic, sau tách ra để dễ mock khi test.
2. **Trích xuất role-guard:** Hai phương thức `requireSeller()`/`requireBidder()` được rút gọn từ logic `if (loggedInUser == null || !(loggedInUser instanceof Seller)) ...` lặp lại nhiều lần ở mỗi handler.
3. **Thay shared-Connection bằng pool:** Comment trong `DBConnection.java` ghi rõ rằng phiên bản cũ dùng một `Connection` chung cho mọi thread, gây corrupt cursor — đã được tái cấu trúc sang HikariCP. Đây là một ví dụ tái cấu trúc kinh điển vì lý do an toàn đa luồng.
4. **Thay `double` bằng `BigDecimal`:** Trong `User.java` còn comment cũ:
   ```text
   /*public boolean updateBalance(BigDecimal amount) {
       if (this.accountBalance + amount >= 0) { ... }       // bản double cũ
   }*/
   ```
   Phiên bản mới dùng `compareTo` trên `BigDecimal` — chính xác cho tiền tệ.
5. **Replace Conditional with Polymorphism:** Trong `UserFactory` chuyển từ so sánh `String` sang `switch` trên `enum UserRole`, vừa nhanh hơn vừa type-safe hơn.
6. **Tách Command Pattern:** `ClientHandler.run()` ban đầu nhiều khả năng là một `switch-case` dài — đã refactor thành `Map<String, RequestProcessor>`, mỗi case là một method nhỏ riêng biệt.

### 4.8. Kiểm thử

Bộ test gồm 3 file dùng **JUnit 5 (Jupiter)**:

| File              | Số test | Phạm vi                                                              |
|:------------------|:--------|:---------------------------------------------------------------------|
| `UserTest`        | 3       | `updateBalance` thành công, thiếu tiền, người bán nhận tiền          |
| `ValidationTest`  | 4       | Validate email, bid amount, payment, state transition                |
| `AuctionRoomTest` | 11      | Bid hợp lệ/không hợp lệ, anti-sniping, auto-bid, tie-breaker, all-in |

**Kỹ thuật test đã dùng:**

- `@BeforeEach` để khởi tạo bidder/item/room sạch cho mỗi test.
- `assertEquals`, `assertTrue`, `assertFalse`, `assertThrows`, `assertDoesNotThrow`.
- **Test exception chứa thông điệp đúng:**
  ```text
  Exception ex = assertThrows(InvalidBidException.class,
                              () -> room.placeBid(bidderA, new BigDecimal("900")));
  assertTrue(ex.getMessage().contains("lớn hơn 1000"));
  ```
- **Test edge case:** auto-bid all-in (`max = 2200`, bước = 500, giá đang 2000 → ép ra 2200 thay vì 2500), tie-breaker (cùng max 3000, ai đăng ký trước thắng), max extensions cho anti-sniping.
- **Test state machine** trong `Validation.canTransitionTo` — kiểm tra cả nhánh hợp lệ lẫn nhảy cóc.

Tổng: 18 unit test phủ kín các nhánh nghiệp vụ phức tạp nhất của hệ thống.

### 4.9. Tích hợp và triển khai

**Maven multi-module** (`auction-server` + `auction-client`):
- `maven-compiler-plugin` cấu hình Java 21 cho server.
- `maven-surefire-plugin` chạy unit test khi `mvn verify`.
- `maven-shade-plugin` đóng gói thành **fat-jar** (`*-shaded.jar`) chứa toàn bộ dependency, chỉ cần `java -jar` là chạy.

**CI/CD bằng GitHub Actions** (`.github/workflows/ci.yml`):

```text
jobs:
  build-server:    setup-java@v4 (Java 21) → mvn clean verify → upload jar
  build-client:    setup-java@v4 (Java 17) → mvn clean compile
  ci-success:      needs: [build-server, build-client]
```

Mỗi push hoặc pull request lên `main`/`master` sẽ tự động:
1. Build server với Java 21, chạy toàn bộ unit test.
2. Compile client với Java 17 (vì JavaFX).
3. Upload báo cáo Surefire và file jar dưới dạng artifact để dev tải về.

**Static analysis bằng Qodana** (`qodana_code_quality.yml` + `qodana.yaml`) — quét code smell, lỗi tiềm ẩn, tuân thủ chuẩn.

Đây là quy trình DevOps cơ bản: mọi commit đều được kiểm tra tự động trước khi merge — không có chuyện "chạy được trên máy tôi" mà CI fail.

---

## 5. Đánh giá tổng thể và hạn chế

### Điểm mạnh

- **Kiến trúc phân tầng chặt chẽ**, tuân thủ separation of concerns.
- **An toàn đa luồng nhiều lớp:** synchronized cấp instance, ConcurrentHashMap, optimistic locking ở DB, HikariCP pool, Virtual Thread cho I/O.
- **Bảo mật:** BCrypt cho mật khẩu, PreparedStatement chống SQL Injection, role guard ở mọi handler nhạy cảm.
- **Logic nghiệp vụ thực tế:** anti-sniping, auto-bid với tie-breaker, settlement có rollback khi thiếu số dư.
- **Có CI/CD và unit test thực sự** — không chỉ là "viết cho có".

### Hạn chế và hướng phát triển

1. **Thông tin DB đang hardcode** (`URL`, `username`, `password` trong `DBConnection.java`). Nên đưa ra `application.properties` hoặc biến môi trường.
2. **Logging dùng `System.out.println` và `System.err.println`.** Nên thay bằng SLF4J + Logback để có level (DEBUG/INFO/WARN/ERROR), file rotation, format chuẩn.
3. **Giao thức tự thiết kế trên Socket thuần** — phù hợp cho đồ án nhưng nếu mở rộng nên cân nhắc WebSocket (tích hợp web tốt hơn) hoặc gRPC (typed schema).
4. **Chưa có integration test** — toàn bộ test hiện tại là unit test ở tầng model/util. Có thể bổ sung test với DB embedded (H2) cho tầng DAO.
5. **`ClientHandler` còn dài (≈580 dòng).** Có thể tách `handle*` ra các Controller riêng theo domain (`AuctionController`, `UserController`, `AdminController`).
6. **Bảng `users` chưa lưu `email` và `fullName`** dù model có sẵn — code đăng ký đã có comment "để dành mở rộng sau".

---

## 6. Kết luận

Backend của AuctionVN không chỉ "chạy được", mà còn vận dụng có chủ đích cả 9 chủ đề kiến thức đã học:

> **Kế thừa & đa hình** trong cây `User`/`Item` → **lớp trừu tượng + giao diện + generic** ở `User`/`Item`/`GenericDAO<T>` → **cấu trúc dữ liệu** chọn đúng (`ConcurrentHashMap` cho map đa luồng, `CopyOnWriteArrayList` cho broadcast list) → **xử lý ngoại lệ** với 5 custom exception và `try-with-resources` → **6 mẫu thiết kế** (Singleton, Factory, DAO, DTO, Command, Strategy ngầm) → **đa luồng** đa cấp (Virtual Thread, synchronized, optimistic lock, CompletableFuture) → **refactoring** có dấu vết rõ ràng (single Connection → HikariCP, double → BigDecimal, switch-case → Map dispatch) → **18 unit test JUnit 5** phủ các nhánh phức tạp → **CI/CD GitHub Actions** + **Qodana** + **Maven shade** đóng gói deploy.

Mỗi lựa chọn công nghệ đều có lý do kỹ thuật rõ ràng — Virtual Thread vì I/O-bound, HikariCP vì shared connection không an toàn, BCrypt vì kháng brute-force, optimistic lock vì nhanh hơn pessimistic. Đây chính là tinh thần của môn học: hiểu *tại sao chọn*, không chỉ *biết dùng*.

---

*Báo cáo này phân tích trực tiếp mã nguồn trong thư mục `auction-server/`. Mọi số liệu (số file, số dòng, số test) đều được kiểm chứng từ code thực tế.*