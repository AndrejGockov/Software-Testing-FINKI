package mk.ukim.finki.testing.booking;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Optional<Registration> findByReference(String reference);
    Page<Registration> findByWorkshopCodeAndAttendeeStartingWith(String code, String prefix, Pageable pageable);
}
