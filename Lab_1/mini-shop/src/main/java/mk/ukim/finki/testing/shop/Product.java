package mk.ukim.finki.testing.shop;

/** Product data returned by the catalog. Stock is a snapshot, not a reservation. */
public record Product(String code, int unitPriceCents, int availableQuantity) {

    public Product {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Product code must not be blank");
        }
        if (unitPriceCents < 1 || unitPriceCents > 1_000_000) {
            throw new IllegalArgumentException("Unit price must be between 1 and 1000000 cents");
        }
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("Available quantity must not be negative");
        }
    }
}
