package mk.ukim.finki.testing.booking;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PostgresCompatibilityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private WorkshopRepository workshops;
    @Autowired
    private RegistrationRepository registrations;
    @Autowired
    private EntityManager entityManager;

    @Test
    void testRoundTripOnPostgres() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("PG-01", "Postgres", true));
        registrations.saveAndFlush(new Registration("PG-REF-1", "Ana", "ana@example.com", "C-01", 2, workshop));
        entityManager.clear();

        Registration loaded = registrations.findByReference("PG-REF-1").orElseThrow();

        assertThat(loaded.getAttendee()).isEqualTo("Ana");
        assertThat(loaded.getTickets()).isEqualTo(2);
        assertThat(loaded.getWorkshop().getCode()).isEqualTo("PG-01");
        assertThat(workshops.findByCode("PG-01").orElseThrow().isOpen()).isTrue();
    }

    @Test
    void testUniqueWorkshopCodeOnPostgres() {
        workshops.saveAndFlush(new Workshop("PG-DUP", "First", true));

        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> workshops.saveAndFlush(new Workshop("PG-DUP", "Second", true)));

        assertThat(exception.getMostSpecificCause().getMessage()).containsIgnoringCase("uk_workshop_code");
    }

    @Test
    void testUniqueReferenceOnPostgres() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("PG-02", "Postgres", true));
        registrations.saveAndFlush(new Registration("PG-REF-D", "Ana", "ana@example.com", "C-01", 1, workshop));

        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> registrations.saveAndFlush(
                        new Registration("PG-REF-D", "Boris", "boris@example.com", "C-01", 1, workshop)));

        assertThat(exception.getMostSpecificCause().getMessage()).containsIgnoringCase("uk_registration_reference");
    }

    @Test
    void testUniqueWorkshopEmailPairOnPostgres() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("PG-03", "Postgres", true));
        registrations.saveAndFlush(new Registration("PG-REF-E1", "Ana", "ana@example.com", "C-01", 1, workshop));

        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> registrations.saveAndFlush(
                        new Registration("PG-REF-E2", "Ana", "ana@example.com", "C-01", 1, workshop)));

        assertThat(exception.getMostSpecificCause().getMessage()).containsIgnoringCase("uk_workshop_email");
    }

    @Test
    void testForeignKeyOnPostgres() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("PG-04", "Postgres", true));
        registrations.saveAndFlush(new Registration("PG-REF-F", "Ana", "ana@example.com", "C-01", 1, workshop));
        entityManager.clear();

        // brisenje na rabotilnica koja ima registracii -> FK greska
        workshops.delete(workshops.findByCode("PG-04").orElseThrow());
        assertThrows(DataIntegrityViolationException.class, () -> workshops.flush());
    }

    @Test
    void testPrefixSearchIsCaseSensitiveOnPostgres() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("PG-05", "Postgres", true));
        registrations.save(new Registration("PG-P-1", "Ana", "p1@example.com", "C-01", 1, workshop));
        registrations.save(new Registration("PG-P-2", "Anabel", "p2@example.com", "C-01", 1, workshop));
        registrations.save(new Registration("PG-P-3", "Boris", "p3@example.com", "C-01", 1, workshop));
        registrations.flush();
        entityManager.clear();

        var page = registrations.findByWorkshopCodeAndAttendeeStartingWith(
                "PG-05", "Ana", PageRequest.of(0, 1, Sort.by("attendee")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Registration::getAttendee).containsExactly("Ana");
        // PostgreSQL LIKE e case-sensitive - "ana" ne treba da pogodi nisto
        assertThat(registrations.findByWorkshopCodeAndAttendeeStartingWith(
                "PG-05", "ana", PageRequest.of(0, 5))).isEmpty();
    }
}
