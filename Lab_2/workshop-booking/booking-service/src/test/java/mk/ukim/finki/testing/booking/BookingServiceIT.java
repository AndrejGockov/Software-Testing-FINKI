package mk.ukim.finki.testing.booking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Integracija: BookingService + Validator + JPA repozitoriumi + H2 baza + HttpMembershipGateway
class BookingServiceIT extends AbstractBookingIT {

    @Autowired
    private BookingService service;

    // --- Uspesen scenario ---

    @Test
    void testRegisterNormalizesValuesAndPersists() {
        // mesani bukvi vo reference/memberId se cuvaat tocno; attendee se stripuva; email -> mali bukvi
        BookingRequest request = request("Reg-01", OPEN, "  Ana  ", "Ana@Example.COM", "C-01", 2);

        RegistrationView view = service.register(request);

        assertThat(view.id()).isNotNull();
        assertThat(view.reference()).isEqualTo("Reg-01");
        assertThat(view.workshopCode()).isEqualTo(OPEN);
        assertThat(view.attendee()).isEqualTo("Ana");
        assertThat(view.email()).isEqualTo("ana@example.com");
        assertThat(view.memberId()).isEqualTo("C-01");
        assertThat(view.tickets()).isEqualTo(2);

        // Sostojba vo bazata (citanje preku nova transakcija - znaci deka e vistinski commit-nato)
        Registration stored = registrations.findByReference("Reg-01").orElseThrow();
        assertThat(stored.getId()).isEqualTo(view.id());
        assertThat(stored.getAttendee()).isEqualTo("Ana");
        assertThat(stored.getEmail()).isEqualTo("ana@example.com");
        assertThat(stored.getMemberId()).isEqualTo("C-01");
        assertThat(stored.getTickets()).isEqualTo(2);
        assertThat(registrations.count()).isEqualTo(1);
        // registry-to e povikano tocno ednash za tocniot clen
        assertThat(REGISTRY.requests()).containsExactly("GET /members/C-01");
    }

    @Test
    void testAttendeeOfExactly100CharactersIncludingSpacesIsAcceptedAndStripped() {
        String attendee = " " + "a".repeat(98) + " "; // 100 karakteri pred stripuvanje

        RegistrationView view = service.register(request("LEN-01", OPEN, attendee, "len@example.com", "C-01", 1));

        assertThat(view.attendee()).isEqualTo("a".repeat(98));
        assertThat(registrations.findByReference("LEN-01").orElseThrow().getAttendee()).isEqualTo("a".repeat(98));
    }

    @Test
    void testTicketBoundariesAreAccepted() {
        assertThat(service.register(request("T-1", OPEN, "Ana", "t1@example.com", "C-01", 1)).tickets()).isEqualTo(1);
        assertThat(service.register(request("T-5", OPEN, "Ana", "t5@example.com", "C-01", 5)).tickets()).isEqualTo(5);
    }

    // --- 1. Validacija e prva: ni repozitorium ni registry ne se dopiraat ---

    static Stream<Arguments> invalidRequests() {
        String tooLong = "a".repeat(101);
        return Stream.of(
                Arguments.of("prazna referenca", request("", OPEN, "Ana", "a@example.com", "C-01", 1)),
                Arguments.of("referenca so prazno mesto", request("REG 01", OPEN, "Ana", "a@example.com", "C-01", 1)),
                Arguments.of("referenca od 33 znaci", request("R".repeat(33), OPEN, "Ana", "a@example.com", "C-01", 1)),
                Arguments.of("workshopCode so _", request("REG-01", "bad_code", "Ana", "a@example.com", "C-01", 1)),
                Arguments.of("workshopCode null", request("REG-01", null, "Ana", "a@example.com", "C-01", 1)),
                Arguments.of("attendee samo prazni mesta", request("REG-01", OPEN, "   ", "a@example.com", "C-01", 1)),
                Arguments.of("attendee od 101 znak", request("REG-01", OPEN, tooLong, "a@example.com", "C-01", 1)),
                Arguments.of("email bez @", request("REG-01", OPEN, "Ana", "not-an-email", "C-01", 1)),
                Arguments.of("email so prazno mesto na pocetok", request("REG-01", OPEN, "Ana", " a@example.com", "C-01", 1)),
                Arguments.of("email so prazno mesto na kraj", request("REG-01", OPEN, "Ana", "a@example.com ", "C-01", 1)),
                Arguments.of("memberId so _", request("REG-01", OPEN, "Ana", "a@example.com", "C_01", 1)),
                Arguments.of("memberId od 33 znaci", request("REG-01", OPEN, "Ana", "a@example.com", "C".repeat(33), 1)),
                Arguments.of("tickets = 0", request("REG-01", OPEN, "Ana", "a@example.com", "C-01", 0)),
                Arguments.of("tickets = 6", request("REG-01", OPEN, "Ana", "a@example.com", "C-01", 6)),
                Arguments.of("tickets = null", request("REG-01", OPEN, "Ana", "a@example.com", "C-01", null))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void testInvalidRequestFailsBeforeTouchingRepositoryOrRegistry(String name, BookingRequest invalid) {
        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.register(invalid));

        assertThat(failure.getStatus()).isEqualTo(400);
        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
        assertThat(REGISTRY.requests()).isEmpty();
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testNullRequestIsInvalidInput() {
        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.register(null));

        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
        assertThat(REGISTRY.requests()).isEmpty();
    }

    @Test
    void testValidationHappensBeforeWorkshopLookup() {
        // i rabotilnicata ne postoi i tickets e nevaliden -> mora INVALID_INPUT (ne WORKSHOP_NOT_FOUND)
        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.register(request("REG-01", "IT-NOPE", "Ana", "a@example.com", "C-01", 0)));

        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
    }

    // --- 2. Rabotilnica: ne postoi / zatvorena ---

    @Test
    void testUnknownWorkshopFailsBeforeMembershipCheck() {
        // clen C-02 e neprikladen, no rabotilnicata se proveruva PRVO
        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.register(request("REG-01", "IT-NOPE", "Ana", "a@example.com", "C-02", 1)));

        assertThat(failure.getStatus()).isEqualTo(404);
        assertThat(failure.getCode()).isEqualTo("WORKSHOP_NOT_FOUND");
        assertThat(REGISTRY.requests()).isEmpty();
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testWorkshopCodeMatchIsExactCaseSensitive() {
        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.register(request("REG-01", OPEN.toLowerCase(), "Ana", "a@example.com", "C-01", 1)));

        assertThat(failure.getCode()).isEqualTo("WORKSHOP_NOT_FOUND");
    }

    @Test
    void testClosedWorkshopFailsBeforeMembershipCheck() {
        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.register(request("REG-01", CLOSED, "Ana", "a@example.com", "C-02", 1)));

        assertThat(failure.getStatus()).isEqualTo(409);
        assertThat(failure.getCode()).isEqualTo("WORKSHOP_CLOSED");
        assertThat(REGISTRY.requests()).isEmpty();
        assertThat(registrations.count()).isZero();
    }

    // --- 3. Clenstvo (registry) ---

    @Test
    void testIneligibleMemberIsRejectedAndNothingIsSaved() {
        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.register(request("REG-01", OPEN, "Ana", "a@example.com", "C-02", 1)));

        assertThat(failure.getStatus()).isEqualTo(422);
        assertThat(failure.getCode()).isEqualTo("MEMBER_INELIGIBLE");
        assertThat(REGISTRY.requests()).containsExactly("GET /members/C-02");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testUnknownMemberIsRejectedAndNothingIsSaved() {
        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.register(request("REG-01", OPEN, "Ana", "a@example.com", "C-99", 1)));

        assertThat(failure.getStatus()).isEqualTo(422);
        assertThat(failure.getCode()).isEqualTo("MEMBER_NOT_FOUND");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testRegistryUnavailableFailsWith503AndNothingIsSaved() {
        REGISTRY.dropConnection();

        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.register(validRequest("REG-01")));

        assertThat(failure.getStatus()).isEqualTo(503);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_UNAVAILABLE");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testRegistryTimeoutFailsWith503AndNothingIsSaved() {
        REGISTRY.delayResponse(); // odgovorot kasni podolgo od registry.read-timeout-ms (1000ms)

        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.register(validRequest("REG-01")));

        assertThat(failure.getStatus()).isEqualTo(503);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_UNAVAILABLE");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testIncompatibleRegistryResponseFailsWith502AndNothingIsSaved() {
        REGISTRY.respondJson(200, "{\"memberId\":\"C-01\",\"eligible\":\"true\"}");

        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.register(validRequest("REG-01")));

        assertThat(failure.getStatus()).isEqualTo(502);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_CONTRACT_ERROR");
        assertThat(registrations.count()).isZero();
    }

    // --- 4. Unikatnost (greski od bazata) ---

    @Test
    void testDuplicateReferenceIsRejectedAndExistingRowIsNotModified() {
        service.register(request("DUP-1", OPEN, "Ana", "ana@example.com", "C-01", 2));

        // ista referenca, drug email/attendee/tickets - bazata mora da go odbie
        assertThrows(DataIntegrityViolationException.class,
                () -> service.register(request("DUP-1", OPEN, "Boris", "boris@example.com", "C-01", 5)));

        assertThat(registrations.count()).isEqualTo(1);
        Registration stored = registrations.findByReference("DUP-1").orElseThrow();
        assertThat(stored.getAttendee()).isEqualTo("Ana");
        assertThat(stored.getEmail()).isEqualTo("ana@example.com");
        assertThat(stored.getTickets()).isEqualTo(2);
    }

    @Test
    void testSameEmailInSameWorkshopIsRejectedEvenWhenCaseDiffers() {
        service.register(request("E-1", OPEN, "Ana", "ana@example.com", "C-01", 1));

        // servisot go pretvora emailot vo mali bukvi -> se sudira so uk_workshop_email
        assertThrows(DataIntegrityViolationException.class,
                () -> service.register(request("E-2", OPEN, "Ana", "ANA@EXAMPLE.COM", "C-01", 1)));

        assertThat(registrations.count()).isEqualTo(1);
        assertThat(registrations.findByReference("E-2")).isEmpty();
    }

    @Test
    void testSameEmailInDifferentWorkshopsIsAllowed() {
        workshops.save(new Workshop("IT-OPEN-2", "Second open workshop", true));
        service.register(request("E-1", OPEN, "Ana", "ana@example.com", "C-01", 1));

        RegistrationView second = service.register(request("E-2", "IT-OPEN-2", "Ana", "ana@example.com", "C-01", 1));

        assertThat(second.workshopCode()).isEqualTo("IT-OPEN-2");
        assertThat(registrations.count()).isEqualTo(2);
    }

    // --- 5. find: mapiranje vo transakcija za citanje ---

    @Test
    void testFindReturnsStoredRegistrationWithWorkshopCode() {
        RegistrationView created = service.register(request("F-1", OPEN, " Ana ", "F@Example.com", "C-01", 3));

        // testot NEMA transakcija - workshop e LAZY, pa uspehot dokazuva deka servisot mapira vo svoja read-tx
        RegistrationView found = service.find("F-1");

        assertThat(found).isEqualTo(created);
        assertThat(found.workshopCode()).isEqualTo(OPEN);
        assertThat(found.attendee()).isEqualTo("Ana");
        assertThat(found.email()).isEqualTo("f@example.com");
    }

    @Test
    void testFindUnknownReferenceFailsWith404() {
        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.find("NO-SUCH-REF"));

        assertThat(failure.getStatus()).isEqualTo(404);
        assertThat(failure.getCode()).isEqualTo("REGISTRATION_NOT_FOUND");
    }
}
