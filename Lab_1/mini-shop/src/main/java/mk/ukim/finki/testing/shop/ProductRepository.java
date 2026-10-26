package mk.ukim.finki.testing.shop;

import java.util.Optional;

public interface ProductRepository {

    /** Returns Optional.empty() if the product code does not exist. */
    Optional<Product> findByCode(String code);
}
