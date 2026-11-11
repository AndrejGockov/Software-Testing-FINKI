package mk.ukim.finki.testing.booking;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingHttpIT extends AbstractBookingIT {

    // isto sto i @LocalServerPort - portot koj go izbral serverot
    @Value("${local.server.port}")
    private int port;

    // klient so ogranicen timeout (nikogash ne cekame beskonecno)
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(8));
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void testCreateAndFetchOverRealHttpPersistsCommittedData() throws Exception {
        HttpResponse<String> created = send("POST", "/registrations", validJson("HTTP-1"));

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).contains("/registrations/HTTP-1");
        assertThat(created.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat((String) JsonPath.read(created.body(), "$.reference")).isEqualTo("HTTP-1");
        assertThat((String) JsonPath.read(created.body(), "$.email")).isEqualTo("http-1@example.com");
        Number id = JsonPath.read(created.body(), "$.id");

        // Serverskata transakcija e zavrsena (commit) - nezavisen citac vo testot go gleda redot
        Registration stored = registrations.findByReference("HTTP-1").orElseThrow();
        assertThat(stored.getId()).isEqualTo(id.longValue());

        HttpResponse<String> fetched = send("GET", "/registrations/HTTP-1", null);
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(fetched.body(), "$.id")).longValue()).isEqualTo(id.longValue());
        assertThat((String) JsonPath.read(fetched.body(), "$.workshopCode")).isEqualTo(OPEN);
    }

    @Test
    void testMalformedJsonOverRealHttpReturns400AndStoresNothing() throws Exception {
        HttpResponse<String> response = send("POST", "/registrations", "{\"reference\":");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("INVALID_INPUT");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testUnknownRegistrationOverRealHttpReturns404() throws Exception {
        HttpResponse<String> response = send("GET", "/registrations/NO-SUCH-REF", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("REGISTRATION_NOT_FOUND");
    }

    @Test
    void testDuplicateOverRealHttpReturns409WithoutChangingStoredRow() throws Exception {
        send("POST", "/registrations", json("DUP-H", OPEN, "Ana", "ana@example.com", "C-01", 2));

        HttpResponse<String> duplicate =
                send("POST", "/registrations", json("DUP-H", OPEN, "Boris", "boris@example.com", "C-01", 5));

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat((String) JsonPath.read(duplicate.body(), "$.code")).isEqualTo("DATA_CONFLICT");
        assertThat(registrations.count()).isEqualTo(1);
        assertThat(registrations.findByReference("DUP-H").orElseThrow().getAttendee()).isEqualTo("Ana");
    }

    @Test
    void testRegistryOutageOverRealHttpReturns503() throws Exception {
        REGISTRY.dropConnection();

        HttpResponse<String> response = send("POST", "/registrations", validJson("HTTP-2"));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("REGISTRY_UNAVAILABLE");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void testBatchRollbackOverRealHttp() throws Exception {
        String body = "[" + validJson("HB-1") + "," + json("HB-2", OPEN, "Ana", "hb-2@example.com", "C-02", 1) + "]";

        HttpResponse<String> response = send("POST", "/registrations/batch", body);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("MEMBER_INELIGIBLE");
        // nezavisen citac: ni prvata stavka ne smee da ostane commit-nata
        assertThat(registrations.count()).isZero();
    }
}
