
package server.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;

public class AuctionManager {
    private static AuctionManager instance;
    private List<AuctionProduct> products;

    private AuctionManager() {
        products = new ArrayList<>();
    }

    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    // Nghiệp vụ 3.1.2: Seller thêm sản phẩm
    public void addProduct(String name, String desc, double startPrice, Date startTime, Date endTime) {
        AuctionProduct product = new AuctionProduct(name, desc, startPrice, startTime, endTime);
        product.setStatus("OPEN");
        products.add(product);
        System.out.println("Đã thêm sản phẩm: " + name + " với trạng thái OPEN");
    }

    // Nghiệp vụ 3.1.4: Cập nhật trạng thái tự động theo thời gian
    public void refreshAuctionStatus() {
        Date now = new Date();
        for (AuctionProduct p : products) {
            if (p.getStatus().equals("OPEN") && now.after(p.getStartTime())) {
                p.setStatus("RUNNING");
            } else if (p.getStatus().equals("RUNNING") && now.after(p.getEndTime())) {
                p.setStatus("FINISHED");
            }
        }
    }

    // Lấy danh sách sản phẩm
    public List<AuctionProduct> getProducts() {
        return products;
    }
}


