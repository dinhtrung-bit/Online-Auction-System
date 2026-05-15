import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.models.items.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ItemFactoryTest — test class mới, thiết kế theo UET.CS2043
 *
 * Kỹ thuật:
 *   [EP]    = Equivalence Partitioning  (slide 10-16)
 *   [BVA]   = Boundary Value Analysis   (slide 11, 17-18)
 *   [2-way] = Pairwise combinatorial    (slide 22-24)
 *   [EG]    = Error Guessing            (slide 9)
 *
 * Unit cần test: ItemFactory.createItem(categoryInfo, itemId, name, startingPrice, description)
 *
 * Phân tích tham số:
 *   - categoryInfo : {ART, ELECTRONICS, VEHICLE, null, rỗng, không hợp lệ}
 *   - startingPrice: số dương / 0 / âm
 *   - itemId       : 0 / dương / âm
 */
public class ItemFactoryTest {

    // ================================================================
    // NHÓM 1: categoryInfo — EP theo lớp danh mục
    // EP lớp HỢP LỆ  : "ART", "ELECTRONICS", "VEHICLE"
    // EP lớp KHÔNG HĐ: null, rỗng, chuỗi không xác định
    // ================================================================

    @Test @DisplayName("[EP] Category = ART → trả về đối tượng Art")
    void testCreate_Art_EP() {
        Item item = ItemFactory.createItem("ART", 1, "Mona Lisa", new BigDecimal("5000"), "desc");
        assertNotNull(item);
        assertInstanceOf(Art.class, item);
        assertEquals("ART", item.getCategoryInfo());
    }

    @Test @DisplayName("[EP] Category = ELECTRONICS → trả về đối tượng Electronics")
    void testCreate_Electronics_EP() {
        Item item = ItemFactory.createItem("ELECTRONICS", 2, "Laptop", new BigDecimal("20000"), "desc");
        assertNotNull(item);
        assertInstanceOf(Electronics.class, item);
        assertEquals("ELECTRONIC", item.getCategoryInfo()); // class trả về "ELECTRONIC"
    }

    @Test @DisplayName("[EP] Category = VEHICLE → trả về đối tượng Vehicle")
    void testCreate_Vehicle_EP() {
        Item item = ItemFactory.createItem("VEHICLE", 3, "Motorbike", new BigDecimal("30000"), "desc");
        assertNotNull(item);
        assertInstanceOf(Vehicle.class, item);
        assertEquals("VEHICLE", item.getCategoryInfo());
    }

    @Test @DisplayName("[EP] Category null — ném IllegalArgumentException")
    void testCreate_NullCategory_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemFactory.createItem(null, 1, "X", new BigDecimal("100"), "d"));
    }

    @Test @DisplayName("[EP] Category rỗng — ném IllegalArgumentException")
    void testCreate_EmptyCategory_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemFactory.createItem("", 1, "X", new BigDecimal("100"), "d"));
    }

    @Test @DisplayName("[EP] Category không tồn tại — ném IllegalArgumentException")
    void testCreate_UnknownCategory_EP() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemFactory.createItem("FURNITURE", 1, "Chair", new BigDecimal("500"), "d"));
    }

    // ================================================================
    // NHÓM 2: categoryInfo — BVA case-sensitivity
    // Boundary: viết thường, viết HOA, viết hỗn hợp
    // ================================================================

    @Test @DisplayName("[BVA] Category 'art' viết thường — factory phải normalize")
    void testCreate_LowercaseArt_BVA() {
        Item item = ItemFactory.createItem("art", 1, "Pic", new BigDecimal("100"), "d");
        assertInstanceOf(Art.class, item);
    }

    @Test @DisplayName("[BVA] Category 'Electronics' viết hỗn hợp — factory phải normalize")
    void testCreate_MixedCaseElectronics_BVA() {
        Item item = ItemFactory.createItem("Electronics", 2, "Phone", new BigDecimal("500"), "d");
        assertInstanceOf(Electronics.class, item);
    }

    @Test @DisplayName("[BVA] Category 'ELECTRONIC' thiếu chữ S — factory dự phòng")
    void testCreate_ElectronicWithoutS_BVA() {
        // Factory có xử lý alias "ELECTRONIC" → "ELECTRONICS" (slide EG)
        Item item = ItemFactory.createItem("ELECTRONIC", 3, "TV", new BigDecimal("300"), "d");
        assertInstanceOf(Electronics.class, item);
    }

    @Test @DisplayName("[EG] Category chỉ có khoảng trắng — ném IllegalArgumentException")
    void testCreate_WhitespaceCategory_EG() {
        // [EG] Input hay gặp từ người dùng: chỉ space
        assertThrows(IllegalArgumentException.class,
                () -> ItemFactory.createItem("   ", 1, "X", new BigDecimal("100"), "d"));
    }

    // ================================================================
    // NHÓM 3: startingPrice — EP + BVA
    // EP: giá dương (hợp lệ) / 0 / âm
    // BVA: 0.01 (nhỏ nhất dương), 0 (zero), -0.01 (âm sát 0)
    //
    // LƯU Ý: ItemFactory không tự validate giá — nó tin tưởng vào tầng gọi.
    // Các test này kiểm tra object được tạo ra đúng startingPrice hay không.
    // ================================================================

    @Test @DisplayName("[EP] Giá khởi điểm dương — tạo item đúng giá")
    void testCreate_PositivePrice_EP() {
        Item item = ItemFactory.createItem("ART", 1, "Pic", new BigDecimal("999.99"), "d");
        assertEquals(new BigDecimal("999.99"), item.getStartingPrice());
    }

    @Test @DisplayName("[BVA] Giá = 0.01 — boundary dương nhỏ nhất")
    void testCreate_SmallestPrice_BVA() {
        Item item = ItemFactory.createItem("ART", 1, "Pic", new BigDecimal("0.01"), "d");
        assertEquals(new BigDecimal("0.01"), item.getStartingPrice());
    }

    @Test @DisplayName("[BVA] Giá = 0 — boundary zero (factory tạo được nhưng giá = 0)")
    void testCreate_ZeroPrice_BVA() {
        Item item = ItemFactory.createItem("ART", 1, "Pic", BigDecimal.ZERO, "d");
        assertEquals(BigDecimal.ZERO, item.getStartingPrice());
    }

    // ================================================================
    // NHÓM 4: các thuộc tính item sau khi tạo — kiểm tra state
    // ================================================================

    @Test @DisplayName("[EP] Tên item được gán đúng")
    void testCreate_NameAssigned_EP() {
        Item item = ItemFactory.createItem("VEHICLE", 5, "Honda Wave", new BigDecimal("15000"), "xe số");
        assertEquals("Honda Wave", item.getName());
    }

    @Test @DisplayName("[EP] Mô tả item được gán đúng")
    void testCreate_DescriptionAssigned_EP() {
        Item item = ItemFactory.createItem("ART", 7, "Sunflower", new BigDecimal("2000"), "Van Gogh style");
        assertEquals("Van Gogh style", item.getDescription());
    }

    @Test @DisplayName("[EP] ItemId được gán đúng")
    void testCreate_ItemIdAssigned_EP() {
        Item item = ItemFactory.createItem("ELECTRONICS", 42, "Tablet", new BigDecimal("1000"), "d");
        assertEquals(42, item.getItemId());
    }

    // ================================================================
    // NHÓM 5: 2-way Pairwise — kết hợp (category × priceLevel) (slide 22-24)
    // category  : {ART, ELECTRONICS, VEHICLE}
    // priceLevel: {small=1, medium=10000, large=999999}
    //
    // TC | category    | price
    //  1 | ART         | 1
    //  2 | ART         | 999999
    //  3 | ELECTRONICS | 10000
    //  4 | ELECTRONICS | 1
    //  5 | VEHICLE     | 999999
    //  6 | VEHICLE     | 10000
    // ================================================================

    @Test @DisplayName("[2-way] ART + giá nhỏ")
    void testPairwise_Art_SmallPrice() {
        Item item = ItemFactory.createItem("ART", 1, "Sketch", new BigDecimal("1"), "d");
        assertInstanceOf(Art.class, item);
        assertEquals(new BigDecimal("1"), item.getStartingPrice());
    }

    @Test @DisplayName("[2-way] ART + giá lớn")
    void testPairwise_Art_LargePrice() {
        Item item = ItemFactory.createItem("ART", 2, "Masterpiece", new BigDecimal("999999"), "d");
        assertInstanceOf(Art.class, item);
        assertEquals(new BigDecimal("999999"), item.getStartingPrice());
    }

    @Test @DisplayName("[2-way] ELECTRONICS + giá trung bình")
    void testPairwise_Electronics_MediumPrice() {
        Item item = ItemFactory.createItem("ELECTRONICS", 3, "Phone", new BigDecimal("10000"), "d");
        assertInstanceOf(Electronics.class, item);
        assertEquals(new BigDecimal("10000"), item.getStartingPrice());
    }

    @Test @DisplayName("[2-way] ELECTRONICS + giá nhỏ")
    void testPairwise_Electronics_SmallPrice() {
        Item item = ItemFactory.createItem("ELECTRONICS", 4, "Cable", new BigDecimal("1"), "d");
        assertInstanceOf(Electronics.class, item);
    }

    @Test @DisplayName("[2-way] VEHICLE + giá lớn")
    void testPairwise_Vehicle_LargePrice() {
        Item item = ItemFactory.createItem("VEHICLE", 5, "Lamborghini", new BigDecimal("999999"), "d");
        assertInstanceOf(Vehicle.class, item);
        assertEquals(new BigDecimal("999999"), item.getStartingPrice());
    }

    @Test @DisplayName("[2-way] VEHICLE + giá trung bình")
    void testPairwise_Vehicle_MediumPrice() {
        Item item = ItemFactory.createItem("VEHICLE", 6, "Motorbike", new BigDecimal("10000"), "d");
        assertInstanceOf(Vehicle.class, item);
    }
}