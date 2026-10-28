package mk.ukim.finki.testing.shop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceCalculatorTest {

    private PriceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PriceCalculator();
    }

    // Shipping = 500 bidejki subtotal < 5000
    @Test
    void testStandardCustomerBelowFreeShippingThreshold() {
        int total = calculator.calculateTotal(1000, 4, false);
        assertEquals(4500, total, "Subtotal under 5000 cents should include 500 cents shipping fee.");
    }

    // Shipping = 0 bidejki subtotal >= 5000
    @Test
    void testStandardCustomerAtFreeShippingThreshold() {
        int total = calculator.calculateTotal(1000, 5, false);
        assertEquals(5000, total, "Shipping should be free when subtotal reaches exactly 5000 cents.");
    }

    @Test
    void testPremiumCustomerDiscountAndFreeShipping() {
        // Subtotal = 4000 * 2 = 8000 centi
        // Premium popust (90% od sumata): 8000 * 90 / 100 = 7200 centi
        // Bidejki 7200 e nad 5000, dostavata e 0, znaci vkupno 7200 centi
        int total = calculator.calculateTotal(4000, 2, true);
        assertEquals(7200, total, "Premium discount should apply 90% and grant free shipping if >= 5000.");
    }

    @Test
    void testPremiumCustomerRoundingDown() {
        // Subtotal = 333 * 3 = 999 centi
        // Popust: (999 * 90) / 100 = 899.1 -> se zaokruzuva na nadole na 899 centi
        // Bidejki 899 e pod 5000, dodavame 500 dostava = 1399 centi vkupno
        int total = calculator.calculateTotal(333, 3, true);
        assertEquals(1399, total, "Premium customer discount should truncate/round down integer math correctly.");
    }

    // Granici za
    @ParameterizedTest
    @CsvSource({
            "0, 5",         // Cena pod dozvoleniot min (1)
            "1000001, 5",   // Cena nad dozvoleniot max (1,000,000)
            "100, 0",       // Kolicina pod min (1)
            "100, 101"      // Kolicina nad max (100)
    })
    void testInvalidParametersThrowIllegalArgumentException(int unitPriceCents, int quantity) {
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateTotal(unitPriceCents, quantity, false);
        }, "Should throw IllegalArgumentException for out-of-bounds price or quantity.");
    }
}