package org.linlinjava.announce.goods.application.util.exception.goods;

public class LitemallInsufficientStockException extends RuntimeException{

    public LitemallInsufficientStockException(String message) {
        super("Insufficient stock for this id: " + message);
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
