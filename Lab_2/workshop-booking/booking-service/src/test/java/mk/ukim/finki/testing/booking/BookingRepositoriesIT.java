package mk.ukim.finki.testing.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class BookingRepositoriesIT {

    @Autowired
    private WorkshopRepository workshops;
    @Autowired
    private RegistrationRepository registrations;
    @Autowired
    private TestEntityManager entityManager;

    // --- WORKSHOP ---

    @Test
    void testWorkshopSaveAndReloadAllFields() {
        Workshop saved = workshops.saveAndFlush(new Workshop("RT-01", "Round trip", true));
        // clear() ja prazni sesijata, pa sledniot upit odi vo bazata (se proveruva mapiranjeto, ne kesot)
        entityManager.clear();

        Workshop loaded = workshops.findByCode("RT-01").orElseThrow();

        assertThat(loaded).isNotSameAs(saved);
        assertThat(loaded.getId()).isNotNull().isEqualTo(saved.getId());
        assertThat(loaded.getCode()).isEqualTo("RT-01");
        assertThat(loaded.getTitle()).isEqualTo("Round trip");
        assertThat(loaded.isOpen()).isTrue();
    }

    @Test
    void testWorkshopClosedFlagIsPersisted() {
        workshops.saveAndFlush(new Workshop("RT-CLOSED", "Closed", false));
        entityManager.clear();

        assertThat(workshops.findByCode("RT-CLOSED").orElseThrow().isOpen()).isFalse();
    }

    @Test
    void testFindByCodeReturnsEmptyWhenNoRowMatches() {
        assertThat(workshops.findByCode("NO-SUCH-CODE")).isEmpty();
    }

    @Test
    void testFindByCodeIsExactAndCaseSensitive() {
        workshops.saveAndFlush(new Workshop("Exact-01", "Exact", true));
        entityManager.clear();

        assertThat(workshops.findByCode("Exact-01")).isPresent();
        assertThat(workshops.findByCode("exact-01")).isEmpty();
        assertThat(workshops.findByCode("Exact")).isEmpty();
    }

    // --- Constraint-i na rabotilnici ---

    @Test
    void testDuplicateWorkshopCodeIsRejected() {
        workshops.saveAndFlush(new Workshop("DUP-01", "First", true));

        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> workshops.saveAndFlush(new Workshop("DUP-01", "Second", true)));

        assertThat(exception.getMostSpecificCause().getMessage()).containsIgnoringCase("uk_workshop_code");
    }

    @Test
    void testWorkshopWithoutTitleIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> workshops.saveAndFlush(new Workshop("NO-TITLE", null, true)));
    }

    // --- REGISTRATION ---

    @Test
    void testRegistrationSaveAndReloadWithWorkshopRelationship() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("REL-01", "Relation", true));
        Registration saved = registrations.saveAndFlush(
                new Registration("REF-01", "Ana", "ana@example.com", "C-01", 3, workshop));
        entityManager.clear();

        Registration loaded = registrations.findByReference("REF-01").orElseThrow();

        assertThat(loaded).isNotSameAs(saved);
        assertThat(loaded.getId()).isNotNull().isEqualTo(saved.getId());
        assertThat(loaded.getReference()).isEqualTo("REF-01");
        assertThat(loaded.getAttendee()).isEqualTo("Ana");
        assertThat(loaded.getEmail()).isEqualTo("ana@example.com");
        assertThat(loaded.getMemberId()).isEqualTo("C-01");
        assertThat(loaded.getTickets()).isEqualTo(3);
        // workshop e LAZY - vo testot ima aktivna transakcija, pa moze da se cita bez LazyInitializationException
        assertThat(loaded.getWorkshop().getCode()).isEqualTo("REL-01");
    }

    @Test
    void testFindByReferenceReturnsEmptyWhenNoRowMatches() {
        assertThat(registrations.findByReference("NO-SUCH-REF")).isEmpty();
    }

    // --- Constraint-i na registracii ---

    @Test
    void testDuplicateReferenceIsRejected() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("CON-01", "Constraints", true));
        registrations.saveAndFlush(new Registration("REF-DUP", "Ana", "ana@example.com", "C-01", 1, workshop));

        // razlicen email, ista referenca -> se krsi samo uk_registration_reference
        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> registrations.saveAndFlush(
                        new Registration("REF-DUP", "Boris", "boris@example.com", "C-01", 1, workshop)));

        assertThat(exception.getMostSpecificCause().getMessage()).containsIgnoringCase("uk_registration_reference");
    }

    @Test
    void testSameEmailInSameWorkshopIsRejected() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("CON-02", "Constraints", true));
        registrations.saveAndFlush(new Registration("REF-A", "Ana", "ana@example.com", "C-01", 1, workshop));

        // razlicna referenca, ist email, ista rabotilnica -> se krsi uk_workshop_email
        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> registrations.saveAndFlush(
                        new Registration("REF-B", "Ana", "ana@example.com", "C-01", 1, workshop)));

        assertThat(exception.getMostSpecificCause().getMessage()).containsIgnoringCase("uk_workshop_email");
    }

    @Test
    void testSameEmailInDifferentWorkshopsIsAllowed() {
        Workshop first = workshops.saveAndFlush(new Workshop("CON-03A", "First", true));
        Workshop second = workshops.saveAndFlush(new Workshop("CON-03B", "Second", true));

        registrations.saveAndFlush(new Registration("REF-A", "Ana", "ana@example.com", "C-01", 1, first));
        registrations.saveAndFlush(new Registration("REF-B", "Ana", "ana@example.com", "C-01", 1, second));
        entityManager.clear();

        assertThat(registrations.findByReference("REF-A")).isPresent();
        assertThat(registrations.findByReference("REF-B")).isPresent();
    }

    @Test
    void testDatabaseItselfDoesNotLowerCaseEmails() {
        // Normalizacijata (toLowerCase) e odgovornost na BookingService, ne na bazata.
        // Direkten zapis na emaili koi se razlikuvaat samo po golemi bukvi ne se kosi so uk_workshop_email.
        Workshop workshop = workshops.saveAndFlush(new Workshop("CON-04", "Case", true));
        registrations.saveAndFlush(new Registration("REF-A", "Ana", "ana@example.com", "C-01", 1, workshop));
        registrations.saveAndFlush(new Registration("REF-B", "Ana", "ANA@EXAMPLE.COM", "C-01", 1, workshop));
        entityManager.clear();

        assertThat(registrations.count()).isEqualTo(2);
    }

    @Test
    void testRegistrationWithoutAttendeeIsRejected() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("CON-05", "Not null", true));

        assertThrows(DataIntegrityViolationException.class,
                () -> registrations.saveAndFlush(
                        new Registration("REF-NULL", null, "ana@example.com", "C-01", 1, workshop)));
    }

    @Test
    void testRegistrationWithoutWorkshopIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> registrations.saveAndFlush(
                        new Registration("REF-NOWS", "Ana", "ana@example.com", "C-01", 1, null)));
    }

    @Test
    void testDeletingWorkshopWithRegistrationsViolatesForeignKey() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("FK-01", "Foreign key", true));
        registrations.saveAndFlush(new Registration("REF-FK", "Ana", "ana@example.com", "C-01", 1, workshop));
        entityManager.clear();

        Workshop managed = workshops.findByCode("FK-01").orElseThrow();
        workshops.delete(managed);

        // DELETE se izvrsuva dopirot pri flush - togash bazata ja odbiva (FK registrations -> workshops)
        assertThrows(DataIntegrityViolationException.class, () -> workshops.flush());
    }

    // --- Prebaruvanje so prefiks i paginacija ---

    @Test
    void testPrefixSearchFiltersByWorkshopAndPaginatesInExplicitOrder() {
        Workshop target = workshops.saveAndFlush(new Workshop("PAGE-A", "Target", true));
        Workshop other = workshops.saveAndFlush(new Workshop("PAGE-B", "Other", true));
        registrations.save(new Registration("P-1", "Anastasija", "p1@example.com", "C-01", 1, target));
        registrations.save(new Registration("P-2", "Ana", "p2@example.com", "C-01", 1, target));
        registrations.save(new Registration("P-3", "Anabel", "p3@example.com", "C-01", 1, target));
        registrations.save(new Registration("P-4", "Boris", "p4@example.com", "C-01", 1, target));
        // druga rabotilnica, ime koe isto pocnuva so "Ana" - ne smee da se pojavi vo rezultatot
        registrations.save(new Registration("P-5", "Ana", "p5@example.com", "C-01", 1, other));
        registrations.flush();
        entityManager.clear();

        Sort byAttendee = Sort.by("attendee");
        Page<Registration> pageOne = registrations
                .findByWorkshopCodeAndAttendeeStartingWith("PAGE-A", "Ana", PageRequest.of(0, 2, byAttendee));
        Page<Registration> pageTwo = registrations
                .findByWorkshopCodeAndAttendeeStartingWith("PAGE-A", "Ana", PageRequest.of(1, 2, byAttendee));

        // 3 poklopuvanja vo PAGE-A (Boris i registracijata od PAGE-B ne se vklucheni)
        assertThat(pageOne.getTotalElements()).isEqualTo(3);
        assertThat(pageOne.getTotalPages()).isEqualTo(2);
        assertThat(pageOne.getContent()).extracting(Registration::getAttendee).containsExactly("Ana", "Anabel");
        assertThat(pageTwo.getContent()).extracting(Registration::getAttendee).containsExactly("Anastasija");
    }

    @Test
    void testPrefixSearchIsCaseSensitiveAndEmptyWhenNothingMatches() {
        Workshop workshop = workshops.saveAndFlush(new Workshop("PAGE-C", "Case", true));
        registrations.saveAndFlush(new Registration("C-1", "Ana", "c1@example.com", "C-01", 1, workshop));
        entityManager.clear();

        assertThat(registrations.findByWorkshopCodeAndAttendeeStartingWith("PAGE-C", "ana", PageRequest.of(0, 5)))
                .isEmpty();
        assertThat(registrations.findByWorkshopCodeAndAttendeeStartingWith("PAGE-C", "Zzz", PageRequest.of(0, 5)))
                .isEmpty();
        assertThat(registrations.findByWorkshopCodeAndAttendeeStartingWith("NO-WORKSHOP", "Ana", PageRequest.of(0, 5)))
                .isEmpty();
    }
}
