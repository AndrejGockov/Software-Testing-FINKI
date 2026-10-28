package mk.ukim.finki.testing.shop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private OrderNotifier orderNotifier;
    private PriceCalculator priceCalculator;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderNotifier = mock(OrderNotifier.class);

        priceCalculator = new PriceCalculator();

        orderService = new OrderService(productRepository, orderRepository, orderNotifier, priceCalculator);
    }

    // Uspesen order
    @Test
    void testPlaceOrderSuccess() {
        // Produkt
        Product product = new Product("PROD-100", 2000, 10);
        when(productRepository.findByCode("PROD-100")).thenReturn(Optional.of(product));

        // Naracka za 2 parcinja
        // 4000 subtotal + 500 shipping = 4500
        Order order = orderService.placeOrder("CUST-1", "PROD-100", 2, false);

        // Proverka: Dali se e tochno presmetano
        assertNotNull(order);
        assertEquals("CUST-1", order.customerId());
        assertEquals("PROD-100", order.productCode());
        assertEquals(2, order.quantity());
        assertEquals(4500, order.totalCents());

        // Proveruvame dali vo baza e zacuvano ednash i dali e pratena notifikacija
        verify(orderRepository, times(1)).save(order);
        verify(orderNotifier, times(1)).sendConfirmation(order);
    }

    // 1. Dali ke pomine customerId
    @Test
    void testValidationOrderCustomerIdFirst() {
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.placeOrder("", "PROD-100", 1, false);
        });
        verifyNoInteractions(productRepository);
    }

    // 2. ProductCode
    @Test
    void testValidationOrderProductCodeSecond() {
        // ProductCode se proveruva vtor po red
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.placeOrder("CUST-1", "  ", 1, false);
        });
        verifyNoInteractions(productRepository);
    }

    // 3. Kolicinata dali e izmegu 1-100
    @Test
    void testValidationOrderQuantityThird() {
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.placeOrder("CUST-1", "PROD-100", 101, false);
        });
        verifyNoInteractions(productRepository);
    }

    // --- ISKLUCOCI ---
    @Test
    void testProductNotFoundThrowsException() {
        when(productRepository.findByCode("NON-EXISTENT")).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> {
            orderService.placeOrder("CUST-1", "NON-EXISTENT", 1, false);
        });

        verify(orderRepository, never()).save(any());
        verify(orderNotifier, never()).sendConfirmation(any());
    }

    @Test
    void testInsufficientStockThrowsException() {
        Product product = new Product("PROD-100", 1000, 3);
        when(productRepository.findByCode("PROD-100")).thenReturn(Optional.of(product));

        assertThrows(InsufficientStockException.class, () -> {
            orderService.placeOrder("CUST-1", "PROD-100", 5, false);
        });

        verify(orderRepository, never()).save(any());
        verify(orderNotifier, never()).sendConfirmation(any());
    }

    // Ako ima problem so bazata pri zacuvuvane, treba da frli greshka i SLUCAJNO da ne prati email
    @Test
    void testSaveFailurePropagatesAndPreventsNotification() {
        Product product = new Product("PROD-100", 1000, 10);
        when(productRepository.findByCode("PROD-100")).thenReturn(Optional.of(product));
        doThrow(new IllegalStateException("Database error")).when(orderRepository).save(any());

        assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder("CUST-1", "PROD-100", 1, false);
        });

        verify(orderNotifier, never()).sendConfirmation(any());
    }

    // Ako pomine cuvanjeto vo baza, ama pukne slenjeto na email.
    // Greshkata treba da ima, ama narackata e zacuvana
    @Test
    void testNotificationFailurePropagatesAfterSaving() {
        Product product = new Product("PROD-100", 1000, 10);
        when(productRepository.findByCode("PROD-100")).thenReturn(Optional.of(product));
        doThrow(new IllegalStateException("Email service down")).when(orderNotifier).sendConfirmation(any());

        assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder("CUST-1", "PROD-100", 1, false);
        });

        verify(orderRepository, times(1)).save(any());
    }
}