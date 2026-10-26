package mk.ukim.finki.testing.shop;

/** Calculates the total for an order containing one product type. All amounts are cents. */
public class PriceCalculator {

    public int calculateTotal(int unitPriceCents, int quantity, boolean premiumCustomer) {
        if (unitPriceCents < 1 || unitPriceCents > 1_000_000) {
            throw new IllegalArgumentException("Unit price must be between 1 and 1000000 cents");
        }
        if (quantity < 1 || quantity > 100) {
            throw new IllegalArgumentException("Quantity must be between 1 and 100");
        }

        int subtotal = unitPriceCents * quantity;
        int discountedSubtotal;
        if (premiumCustomer == true) {
            discountedSubtotal = (int) (subtotal * 90L / 100);
        } else {
            discountedSubtotal = subtotal;
        }
        int shipping = 500;
        if (discountedSubtotal >= 5_000)
            shipping = 0;
        return discountedSubtotal + shipping;
    }
}
