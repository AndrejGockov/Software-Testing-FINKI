package mk.ukim.finki.testing.shop;

import java.util.Objects;
import java.util.ArrayList;

public class OrderService {

    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderNotifier notifier;
    private final PriceCalculator calculator;

    public OrderService(ProductRepository products, OrderRepository orders,
                        OrderNotifier notifier, PriceCalculator calculator) {
        this.products = Objects.requireNonNull(products);
        this.orders = Objects.requireNonNull(orders);
        this.notifier = Objects.requireNonNull(notifier);
        this.calculator = Objects.requireNonNull(calculator);
    }

    public Order placeOrder(String customerId, String productCode,
                            int quantity, boolean premiumCustomer) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID must not be blank");
        }
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("Product code must not be blank");
        }
        if (quantity < 1 || quantity > 100) {
            throw new IllegalArgumentException("Quantity must be between 1 and 100");
        }

        Product product = products.findByCode(productCode)
                .orElseThrow(() -> new ProductNotFoundException(productCode));
        if (quantity > product.availableQuantity()) {
            throw new InsufficientStockException(quantity, product.availableQuantity());
        }

        int total = calculator.calculateTotal(product.unitPriceCents(), quantity, premiumCustomer);
        Order order = new Order(customerId, productCode, quantity, total);
        orders.save(order);
        notifier.sendConfirmation(order);
        return order;
    }
}
