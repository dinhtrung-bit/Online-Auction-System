package server.models.auction;

import server.models.users.Bidder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AutoBidConfig {
    private Bidder bidder;
    private BigDecimal maxBid;      // Mức giá tối đa người này chịu chi
    private BigDecimal increment;   // Bước giá mỗi lần tự động tăng (vd: +100k)
    private LocalDateTime registerTime; // Thời điểm đăng ký (để ưu tiên người đăng ký trước)

    public AutoBidConfig(Bidder bidder, BigDecimal maxBid, BigDecimal increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registerTime = LocalDateTime.now();
    }

    public Bidder getBidder() { return bidder; }
    public BigDecimal getMaxBid() { return maxBid; }
    public BigDecimal getIncrement() { return increment; }
    public LocalDateTime getRegisterTime() { return registerTime; }
}