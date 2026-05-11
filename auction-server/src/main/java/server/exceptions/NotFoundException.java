package server.exceptions;

public class NotFoundException extends Exception {
    public NotFoundException(String message) {
        super(message);
    }
}
//Dùng khi findById, findByUsername, findBySellerId không tìm thấy dữ liệu.