package mk.ukim.finki.testing.shop;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(int requested, int available) {
        super("Requested quantity " + requested + " exceeds available quantity " + available);
    }
}
