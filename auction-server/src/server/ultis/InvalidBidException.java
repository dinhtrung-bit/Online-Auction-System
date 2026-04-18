<<<<<<< HEAD
package server
        .ultis;
=======
package src.server.ultis;
>>>>>>> e6fdc0c29820aeb4b000a79a18715e91b6bec51e

public class InvalidBidException extends Exception {
    public InvalidBidException(String message) {
        super(message); // giá đăt phải cao hơn giá hiện tại
    }
}