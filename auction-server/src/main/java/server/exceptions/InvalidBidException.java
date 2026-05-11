package server
        .exceptions;

public class InvalidBidException extends Exception {
    public InvalidBidException(String message) {
        super(message); // giá đăt phải cao hơn giá hiện tại
    }
}