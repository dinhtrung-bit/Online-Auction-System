package server.dao.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import server.exceptions.DatabaseConnectionException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DBConnection — Singleton wrapper cho HikariCP connection pool.
 *
 * Lý do thay thế single-Connection:
 *   - Single Connection cũ chia sẻ 1 object Connection cho nhiều Virtual Thread đồng thời
 *     → cursor state bị corrupt, dữ liệu sai, có thể NPE hoặc PSQLException.
 *   - HikariCP cấp mỗi thread 1 Connection độc lập từ pool, tự trả lại khi try-with-resources
 *     đóng, an toàn hoàn toàn với đa luồng.
 */
public class DBConnection {

    private static final String URL      = "jdbc:mysql://localhost:3306/daugia"
            + "?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true";
    private static final String DB_USER  = "root";
    private static final String PASSWORD = "";

    private static volatile HikariDataSource dataSource;

    private DBConnection() {} // utility class — không cho khởi tạo

    /** Khởi tạo pool lần đầu (lazy, thread-safe). */
    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DBConnection.class) {
                if (dataSource == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(URL);
                    config.setUsername(DB_USER);
                    config.setPassword(PASSWORD);
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");

                    // Pool size: 10 connection thường trực, tối đa 20
                    config.setMinimumIdle(5);
                    config.setMaximumPoolSize(20);

                    // Timeout lấy connection từ pool: 30s
                    config.setConnectionTimeout(30_000);
                    // Giữ idle connection tối đa 10 phút
                    config.setIdleTimeout(600_000);
                    // Connection sống tối đa 30 phút
                    config.setMaxLifetime(1_800_000);

                    config.setPoolName("AuctionDB-Pool");

                    dataSource = new HikariDataSource(config);
                    System.out.println(">>> [DB] HikariCP pool đã khởi động.");
                }
            }
        }
        return dataSource;
    }

    /**
     * Lấy một Connection từ pool.
     * Luôn dùng trong try-with-resources để tự động trả về pool:
     * <pre>
     *   try (Connection conn = DBConnection.getInstance()) { ... }
     * </pre>
     */
    public static Connection getInstance() {
        try {
            return getDataSource().getConnection();
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Không lấy được Connection từ pool", e);
        }
    }

    /** Đóng pool khi server tắt (gọi trong shutdown hook). */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println(">>> [DB] Pool đã đóng.");
        }
    }
}