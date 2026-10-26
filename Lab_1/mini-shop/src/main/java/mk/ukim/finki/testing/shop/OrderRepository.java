package mk.ukim.finki.testing.shop;

public interface OrderRepository {

    /** May throw IllegalStateException if the order cannot be saved. */
    void save(Order order);
}
