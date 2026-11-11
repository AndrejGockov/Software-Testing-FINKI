package mk.ukim.finki.testing.registry;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracija so VISTINSKA HTTP komunikacija: membership-registry startuva na slucajna porta (RANDOM_PORT),
 * a se povikuva preku socket so java.net.http.HttpClient - istiot tip klient koj go koristi consumer-ot.
 *
 * Proveruva sirov HTTP odgovor: status, Content-Type zaglavie (consumer-ot go bara za JSON deserijalizacija)
 * i telo. Portot ne e 9091, pa testot ne zavisi od racno startuvan proces.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MemberProviderHttpIT {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void testEligibleMemberOverRealHttp() throws Exception {
        HttpResponse<String> response = get("/members/C-01");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat((String) JsonPath.read(response.body(), "$.memberId")).isEqualTo("C-01");
        assertThat((Boolean) JsonPath.read(response.body(), "$.eligible")).isTrue();
    }

    @Test
    void testIneligibleMemberOverRealHttp() throws Exception {
        HttpResponse<String> response = get("/members/C-02");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.memberId")).isEqualTo("C-02");
        assertThat((Boolean) JsonPath.read(response.body(), "$.eligible")).isFalse();
    }

    @Test
    void testUnknownMemberOverRealHttp() throws Exception {
        HttpResponse<String> response = get("/members/C-99");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("MEMBER_NOT_FOUND");
    }
}
