package mk.ukim.finki.testing.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractBookingIT {

    // Eden stub server za cela JVM; pred sekoj test se vraka na pocetna sostojba (reset)
    protected static final StubRegistryServer REGISTRY = StubRegistryServer.start();

    // Testovite si gi kreiraat sopstvenite rabotilnici (prefiks "IT-") - ne zavisat od JAVA-01 / CLOSED-01
    protected static final String OPEN = "IT-OPEN";
    protected static final String CLOSED = "IT-CLOSED";

    @DynamicPropertySource
    static void registryProperties(DynamicPropertyRegistry registry) {
        // HttpMembershipGateway ke gi povikuva stub-ot, ne porta 9091
        registry.add("registry.base-url", REGISTRY::baseUrl);
    }

    @Autowired
    protected WorkshopRepository workshops;
    @Autowired
    protected RegistrationRepository registrations;

    @BeforeEach
    void prepareEnvironment() {
        REGISTRY.reset();
        cleanDatabase();
        workshops.save(new Workshop(OPEN, "Open workshop", true));
        workshops.save(new Workshop(CLOSED, "Closed workshop", false));
    }

    @AfterEach
    void cleanupEnvironment() {
        REGISTRY.reset();
        cleanDatabase();
    }

    // Prvo registracii (FK kon workshops), potoa samo nasite rabotilnici (IT-*).
    // Sample rabotilnicite od SampleWorkshops (JAVA-01, CLOSED-01) ne gi dopirame.
    private void cleanDatabase() {
        registrations.deleteAllInBatch();
        workshops.deleteAll(workshops.findAll().stream()
                .filter(workshop -> workshop.getCode().startsWith("IT-"))
                .toList());
    }

    // --- Pomosni metodi za gradenje na podatoci ---

    protected static BookingRequest request(String reference, String workshopCode, String attendee,
                                            String email, String memberId, Integer tickets) {
        return new BookingRequest(reference, workshopCode, attendee, email, memberId, tickets);
    }

    /** Validno baranje za otvorenata rabotilnica; email se izveduva od referencata za da bide unikaten. */
    protected static BookingRequest validRequest(String reference) {
        return request(reference, OPEN, "Ana", reference.toLowerCase() + "@example.com", "C-01", 2);
    }

    protected static String json(String reference, String workshopCode, String attendee,
                                 String email, String memberId, Object tickets) {
        return """
                {"reference":"%s","workshopCode":"%s","attendee":"%s","email":"%s","memberId":"%s","tickets":%s}
                """.formatted(reference, workshopCode, attendee, email, memberId, tickets);
    }

    protected static String validJson(String reference) {
        return json(reference, OPEN, "Ana", reference.toLowerCase() + "@example.com", "C-01", 2);
    }
}
