package mk.ukim.finki.testing.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMembershipGatewayIT {

    private StubRegistryServer registry;
    private HttpMembershipGateway gateway;

    @BeforeEach
    void startStubAndGateway() {
        registry = StubRegistryServer.start();
        // golemi timeout-i (2s) za normalnite scenaria - da nema nestabilnost na bavni masini
        gateway = new HttpMembershipGateway(registry.baseUrl(), 2000, 2000);
    }

    @AfterEach
    void stopStub() {
        registry.stop();
    }

    // --- Uspesni scenaria ---

    @Test
    void testEligibleMemberReturnsTrue() {
        assertThat(gateway.isEligible("C-01")).isTrue();
        // consumer-ot mora da go povika tocniot URL so tocniot HTTP metod
        assertThat(registry.requests()).containsExactly("GET /members/C-01");
    }

    @Test
    void testIneligibleMemberReturnsFalse() {
        assertThat(gateway.isEligible("C-02")).isFalse();
    }

    @Test
    void testAdditionalResponseFieldsAreAllowed() {
        // dogovorot dozvoluva dopolnitelni polinja (kompatibilnost nanazad/napred)
        registry.respondJson(200, "{\"memberId\":\"C-01\",\"eligible\":true,\"tier\":\"gold\",\"meta\":{\"a\":1}}");

        assertThat(gateway.isEligible("C-01")).isTrue();
    }

    // --- 404 = nepoznat clen ---

    @Test
    void testUnknownMemberMapsTo422MemberNotFound() {
        BookingFailure failure = assertThrows(BookingFailure.class, () -> gateway.isEligible("C-99"));

        assertThat(failure.getStatus()).isEqualTo(422);
        assertThat(failure.getCode()).isEqualTo("MEMBER_NOT_FOUND");
    }

    @Test
    void testNotFoundIsClassifiedByStatusNotByBody() {
        // consumer-ot ne bara telo na 404 - dovolen e samo statusot
        registry.respondWith(404, "text/plain", "");

        BookingFailure failure = assertThrows(BookingFailure.class, () -> gateway.isEligible("C-01"));

        assertThat(failure.getCode()).isEqualTo("MEMBER_NOT_FOUND");
    }

    // --- Povredi na dogovorot -> 502 REGISTRY_CONTRACT_ERROR ---

    static Stream<Arguments> contractViolations() {
        return Stream.of(
                Arguments.of("eligible e string, ne boolean", 200, "application/json",
                        "{\"memberId\":\"C-01\",\"eligible\":\"true\"}"),
                Arguments.of("nedostasuva eligible", 200, "application/json", "{\"memberId\":\"C-01\"}"),
                Arguments.of("nedostasuva memberId", 200, "application/json", "{\"eligible\":true}"),
                Arguments.of("memberId e drug", 200, "application/json", "{\"memberId\":\"C-02\",\"eligible\":true}"),
                Arguments.of("memberId ne e string", 200, "application/json", "{\"memberId\":1,\"eligible\":true}"),
                Arguments.of("eligible e null", 200, "application/json", "{\"memberId\":\"C-01\",\"eligible\":null}"),
                Arguments.of("nevaliden JSON", 200, "application/json", "{not json"),
                Arguments.of("JSON niza namesto objekt", 200, "application/json", "[true]"),
                Arguments.of("prazen odgovor", 200, "application/json", ""),
                Arguments.of("HTTP 500", 500, "application/json", "{\"code\":\"BOOM\"}"),
                Arguments.of("HTTP 400", 400, "application/json", "{\"code\":\"BAD\"}"),
                Arguments.of("HTTP 503 od registry", 503, "application/json", "{}")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractViolations")
    void testContractViolationsMapTo502(String name, int status, String contentType, String body) {
        registry.respondWith(status, contentType, body);

        BookingFailure failure = assertThrows(BookingFailure.class, () -> gateway.isEligible("C-01"));

        assertThat(failure.getStatus()).isEqualTo(502);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_CONTRACT_ERROR");
    }

    // --- Problemi so mrezata -> 503 REGISTRY_UNAVAILABLE ---

    @Test
    void testConnectionRefusedMapsTo503() {
        // go zapamtuvame URL-ot pa go gasime serverot - na taa porta nema nikoj da slusa
        String deadUrl = registry.baseUrl();
        registry.stop();
        HttpMembershipGateway deadGateway = new HttpMembershipGateway(deadUrl, 500, 500);

        BookingFailure failure = assertThrows(BookingFailure.class, () -> deadGateway.isEligible("C-01"));

        assertThat(failure.getStatus()).isEqualTo(503);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_UNAVAILABLE");
    }

    @Test
    void testConnectionClosedWithoutResponseMapsTo503() {
        registry.dropConnection();

        BookingFailure failure = assertThrows(BookingFailure.class, () -> gateway.isEligible("C-01"));

        assertThat(failure.getStatus()).isEqualTo(503);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_UNAVAILABLE");
    }

    @Test
    void testReadTimeoutMapsTo503AndDoesNotWaitForSlowServer() {
        registry.delayResponse(); // stub-ot "drzi" odgovor do 10s
        HttpMembershipGateway impatient = new HttpMembershipGateway(registry.baseUrl(), 1000, 300);

        long start = System.nanoTime();
        BookingFailure failure = assertThrows(BookingFailure.class, () -> impatient.isEligible("C-01"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(failure.getStatus()).isEqualTo(503);
        assertThat(failure.getCode()).isEqualTo("REGISTRY_UNAVAILABLE");
        // timeout-ot (300ms) mora da deluva - klientot ne smee da ceka celi 10s
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }
}
