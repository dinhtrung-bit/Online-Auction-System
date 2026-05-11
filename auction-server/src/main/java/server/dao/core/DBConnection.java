package server.dao.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import server.exceptions.DatabaseConnectionException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DBConnection — Singleton wrapper cho HikariCP connection pool.
 *
 * Credentials được đọc từ file config.properties trong classpath
 * (src/main/resources/config.properties) thay vì hard-code trực tiếp,
 * giúp tránh lộ thông tin nhạy cảm khi push lên GitHub.
 *
 * Lý do dùng HikariCP thay vì single-Connection:
 *   - Single Connection chia sẻ 1 object cho nhiều Virtual Thread
 *     → cursor state bị corrupt, dữ liệu sai, có thể NPE.
 *   - HikariCP cấp mỗi thread 1 Connection độc lập từ pool,
 *     tự trả lại khi try-with-resources đóng — an toàn hoàn toàn.
 */
public class DBConnection {

    private static volatile HikariDataSource dataSource;

    private DBConnection() {} // utility class — không cho khởi tạo

    /** Đọc config từ classpath:config.properties */
    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = DBConnection.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new DatabaseConnectionException(
                        "Không tìm thấy file config.properties trong resources. " +
                                "Hãy copy config.properties.example thành config.properties và điền thông tin DB.", null);
            }
            props.load(in);
        } catch (IOException e) {
            throw new DatabaseConnectionException("Lỗi đọc file config.properties", e);
        }
        return props;
    }

    /** Khởi tạo pool lần đầu (lazy, thread-safe). */
    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DBConnection.class) {
                if (dataSource == null) {
                    Properties props = loadConfig();

                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(props.getProperty("db.url"));
                    config.setUsername(props.getProperty("db.user"));
                    config.setPassword(props.getProperty("db.password"));
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");

                    config.setMinimumIdle(5);
                    config.setMaximumPoolSize(20);
                    config.setConnectionTimeout(30_000);
                    config.setIdleTimeout(600_000);
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