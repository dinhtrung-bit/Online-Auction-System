package server.exceptions;

public class DuplicateDataException extends Exception {
    public DuplicateDataException(String message) {
        super(message);
    }
}//Dùng khi dữ liệu bị trùng, ví dụ username/email đã tồn tại.