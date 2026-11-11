package mk.ukim.finki.testing.booking;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class HttpMembershipGateway implements MembershipGateway {
    private final RestClient client;

    public HttpMembershipGateway(@Value("${registry.base-url}") String baseUrl,
            @Value("${registry.connect-timeout-ms:1000}") long connectTimeout,
            @Value("${registry.read-timeout-ms:1500}") long readTimeout) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public boolean isEligible(String memberId) {
        try {
            Map<?, ?> body = client.get().uri("/members/{memberId}", memberId).retrieve().body(Map.class);
            if (body == null || !(body.get("memberId") instanceof String actualId)
                    || !memberId.equals(actualId) || !(body.get("eligible") instanceof Boolean eligible)) {
                throw new BookingFailure(502, "REGISTRY_CONTRACT_ERROR");
            }
            return eligible;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BookingFailure(422, "MEMBER_NOT_FOUND");
        } catch (ResourceAccessException e) {
            throw new BookingFailure(503, "REGISTRY_UNAVAILABLE");
        } catch (RestClientException e) {
            throw new BookingFailure(502, "REGISTRY_CONTRACT_ERROR");
        }
    }
}
