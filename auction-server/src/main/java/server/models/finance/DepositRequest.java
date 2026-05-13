package server.models.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DepositRequest — yêu cầu nạp tiền chờ Admin duyệt.
 * Tiền KHÔNG cộng vào ví ngay khi Bidder/Seller gửi yêu cầu.
 */
public class DepositRequest {
    private int id;
    private int userId;
    private String username;
    private BigDecimal amount;
    private String status; // PENDING, APPROVED, REJECTED
    private String note;
    private Integer adminId;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public DepositRequest() {}

    public DepositRequest(int id, int userId, String username, BigDecimal amount, String status,
                          String note, Integer adminId, String adminNote,
                          LocalDateTime createdAt, LocalDateTime reviewedAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.amount = amount;
        this.status = status;
        this.note = note;
        this.adminId = adminId;
        this.adminNote = adminNote;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getAdminId() { return adminId; }
    public void setAdminId(Integer adminId) { this.adminId = adminId; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
