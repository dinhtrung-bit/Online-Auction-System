package server.exceptions;

public class DAOException extends Exception {
    public DAOException(String message) {
        super(message);
    }

    public DAOException(String message, Throwable cause) {
        super(message, cause);
    }
}
//Dùng cho lỗi chung khi thao tác DAO: insert, update, delete, find.