package mk.ukim.finki.testing.shop;

/** Immutable order data created by OrderService. All amounts are cents. */
public record Order(String customerId, String productCode, int quantity, int totalCents) {
}
