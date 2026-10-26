package mk.ukim.finki.testing.shop;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productCode) {
        super("Product not found: " + productCode);
    }
}
