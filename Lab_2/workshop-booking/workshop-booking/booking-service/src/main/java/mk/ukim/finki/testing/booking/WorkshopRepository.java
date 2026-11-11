package mk.ukim.finki.testing.booking;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkshopRepository extends JpaRepository<Workshop, Long> {
    Optional<Workshop> findByCode(String code);
}
