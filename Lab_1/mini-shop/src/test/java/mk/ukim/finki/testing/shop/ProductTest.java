package mk.ukim.finki.testing.shop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    // Getter test
    @Test
    void testValidProductCreation() {
        Product product = new Product("PROD-001", 1500, 20);
        assertEquals("PROD-001", product.code());
        assertEquals(1500, product.unitPriceCents());
        assertEquals(20, product.availableQuantity());
    }

    // Product ne smee da bide null/prazen
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void testInvalidCodeThrowsException(String invalidCode) {
        assertThrows(IllegalArgumentException.class, () -> {
            new Product(invalidCode, 1000, 10);
        }, "Product code must not be null, empty, or blank.");
    }

    // Cena mora da e izmegu 1 - 1,000,000 centi
    @Test
    void testInvalidUnitPriceThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new Product("PROD-01", 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new Product("PROD-01", 1000001, 5));
    }

    // Quantity ne smee da e negativen
    @Test
    void testNegativeStockQuantityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new Product("PROD-01", 500, -1));
    }
}