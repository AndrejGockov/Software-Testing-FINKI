package mk.ukim.finki.testing.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingBatchTransactionIT extends AbstractBookingIT {

    @Autowired
    private BookingService service;

    // --- Uspeh: commit na cel batch ---

    @Test
    void testBatchCommitsAllItemsInInputOrder() {
        List<RegistrationView> result = service.registerBatch(
                List.of(validRequest("B-1"), validRequest("B-2"), validRequest("B-3")));

        assertThat(result).extracting(RegistrationView::reference).containsExactly("B-1", "B-2", "B-3");
        assertThat(result).extracting(RegistrationView::id).doesNotContainNull().doesNotHaveDuplicates();
        // vidlivo i za nezavisen citac -> znaci commit-nato
        assertThat(registrations.count()).isEqualTo(3);
        assertThat(registrations.findByReference("B-2").orElseThrow().getEmail()).isEqualTo("b-2@example.com");
        assertThat(REGISTRY.requests()).hasSize(3);
    }

    @Test
    void testBatchOfExactlyTenIsAccepted() {
        List<BookingRequest> ten = IntStream.rangeClosed(1, 10).mapToObj(i -> validRequest("TEN-" + i)).toList();

        assertThat(service.registerBatch(ten)).hasSize(10);
        assertThat(registrations.count()).isEqualTo(10);
    }

    // --- Granici na golemina na batch ---

    @Test
    void testEmptyBatchIsRejected() {
        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.registerBatch(List.of()));

        assertThat(failure.getStatus()).isEqualTo(400);
        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void testNullBatchIsRejected() {
        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.registerBatch(null));

        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void testBatchOfElevenIsRejectedWithoutAnySideEffect() {
        List<BookingRequest> eleven = IntStream.rangeClosed(1, 11).mapToObj(i -> validRequest("ELV-" + i)).toList();

        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.registerBatch(eleven));

        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
        assertThat(registrations.count()).isZero();
        assertThat(REGISTRY.requests()).isEmpty(); // goleminata se proveruva pred bilo kakov povik
    }

    // --- Rollback: pogresna stavka posle uspesni stavki ---

    @Test
    void testBatchRollsBackWhenLaterMemberIsIneligible() {
        BookingRequest first = validRequest("RB-1");
        BookingRequest second = request("RB-2", OPEN, "Ana", "rb-2@example.com", "C-02", 1); // neprikladen clen

        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.registerBatch(List.of(first, second)));

        assertThat(failure.getCode()).isEqualTo("MEMBER_INELIGIBLE");
        // prvata stavka bila saveAndFlush-nata, no rollback-ot ja otstranuva
        assertThat(registrations.findByReference("RB-1")).isEmpty();
        assertThat(registrations.count()).isZero();
        // ... no povikot kon registry za dvete stavki se sluchil i ne se ponistuva
        assertThat(REGISTRY.requests()).containsExactly("GET /members/C-01", "GET /members/C-02");
    }

    @Test
    void testBatchRollsBackWhenLaterItemFailsValidation() {
        BookingRequest first = validRequest("RV-1");
        BookingRequest invalid = request("RV-2", OPEN, "Ana", "rv-2@example.com", "C-01", 6); // tickets > 5

        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.registerBatch(List.of(first, invalid)));

        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
        assertThat(registrations.count()).isZero();
        // samo prvata stavka stigna do registry; vtorata padna na validacija pred toa
        assertThat(REGISTRY.requests()).containsExactly("GET /members/C-01");
    }

    @Test
    void testBatchRollsBackWhenLaterItemIsNull() {
        List<BookingRequest> withNull = Arrays.asList(validRequest("RN-1"), null);

        BookingFailure failure = assertThrows(BookingFailure.class, () -> service.registerBatch(withNull));

        assertThat(failure.getCode()).isEqualTo("INVALID_INPUT");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testBatchRollsBackWhenLaterWorkshopIsClosed() {
        BookingRequest first = validRequest("RW-1");
        BookingRequest second = request("RW-2", CLOSED, "Ana", "rw-2@example.com", "C-01", 1);

        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.registerBatch(List.of(first, second)));

        assertThat(failure.getCode()).isEqualTo("WORKSHOP_CLOSED");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testBatchRollsBackWhenLaterRegistryResponseViolatesContract() {
        // Stub-ot na SEKOE baranje odgovara so memberId C-01. Za prvata stavka (C-01) toa e tocno,
        // a za vtorata (C-02) memberId ne se poklopuva so baraniot -> povreda na dogovorot (502).
        REGISTRY.respondJson(200, "{\"memberId\":\"C-01\",\"eligible\":true}");
        BookingRequest first = validRequest("RU-1");
        BookingRequest second = request("RU-2", OPEN, "Ana", "ru-2@example.com", "C-02", 1);

        BookingFailure failure = assertThrows(BookingFailure.class,
                () -> service.registerBatch(List.of(first, second)));

        assertThat(failure.getStatus()).isEqualTo(502);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_CONTRACT_ERROR");
        assertThat(registrations.count()).isZero();
    }

    // --- Rollback: greska od baza (constraint) NAKON flush na prethodna stavka ---

    @Test
    void testBatchRollsBackWhenSecondItemDuplicatesFirstReference() {
        BookingRequest first = request("SAME", OPEN, "Ana", "one@example.com", "C-01", 1);
        BookingRequest duplicate = request("SAME", OPEN, "Boris", "two@example.com", "C-01", 1);

        // prvata stavka e vekje flush-nata koga vtorata go krsi uk_registration_reference
        assertThrows(DataIntegrityViolationException.class,
                () -> service.registerBatch(List.of(first, duplicate)));

        assertThat(registrations.findByReference("SAME")).isEmpty();
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testBatchFailureLeavesPreviouslyCommittedRowsUnchanged() {
        // 1) obicna registracija koja e commit-nata NADVOR od batch-ot
        RegistrationView existing = service.register(request("OLD-1", OPEN, "Ana", "old@example.com", "C-01", 2));

        // 2) batch: nova stavka (uspesna) + stavka so ista referenca kako OLD-1 (padnuva na uk_registration_reference)
        BookingRequest fresh = validRequest("NEW-1");
        BookingRequest clash = request("OLD-1", OPEN, "Boris", "boris@example.com", "C-01", 5);
        assertThrows(DataIntegrityViolationException.class, () -> service.registerBatch(List.of(fresh, clash)));

        // novata stavka ja nema, a starata e netknata
        assertThat(registrations.findByReference("NEW-1")).isEmpty();
        assertThat(registrations.count()).isEqualTo(1);
        Registration stored = registrations.findByReference("OLD-1").orElseThrow();
        assertThat(stored.getId()).isEqualTo(existing.id());
        assertThat(stored.getAttendee()).isEqualTo("Ana");
        assertThat(stored.getTickets()).isEqualTo(2);
    }

    @Test
    void testBatchRollsBackWhenSameEmailAppearsTwiceInSameWorkshop() {
        BookingRequest first = request("EM-1", OPEN, "Ana", "same@example.com", "C-01", 1);
        BookingRequest second = request("EM-2", OPEN, "Ana", "SAME@example.com", "C-01", 1);

        assertThrows(DataIntegrityViolationException.class, () -> service.registerBatch(List.of(first, second)));

        assertThat(registrations.count()).isZero();
    }

    // --- Sistemot e iskoristliv i posle neuspesen batch ---

    @Test
    void testSystemStillWorksAfterFailedBatch() {
        assertThrows(DataIntegrityViolationException.class, () -> service.registerBatch(List.of(
                request("AF-1", OPEN, "Ana", "af1@example.com", "C-01", 1),
                request("AF-1", OPEN, "Ana", "af2@example.com", "C-01", 1))));

        // istata referenca (AF-1) sega mora da moze da se iskoristi, bidejki ni eden red ne ostanal
        RegistrationView view = service.register(request("AF-1", OPEN, "Ana", "af1@example.com", "C-01", 1));

        assertThat(view.id()).isNotNull();
        assertThat(registrations.count()).isEqualTo(1);
    }
}
