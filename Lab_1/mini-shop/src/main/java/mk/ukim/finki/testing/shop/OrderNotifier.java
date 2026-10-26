package mk.ukim.finki.testing.shop;

public interface OrderNotifier {

    /** May throw IllegalStateException if the notification cannot be delivered. */
    void sendConfirmation(Order order);
}
