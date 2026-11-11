package mk.ukim.finki.testing.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;

class BookingStartupIT extends AbstractBookingIT {

    @Autowired
    private SampleWorkshops sampleWorkshops;
    @Autowired
    private MembershipGateway membershipGateway;

    @Test
    void testContextUsesTheRealHttpGateway() {
        assertThat(membershipGateway).isInstanceOf(HttpMembershipGateway.class);
    }

    @Test
    void testRunnerCreatesSampleWorkshopsExactlyOnceEvenWhenRunAgain() {
        // runner-ot veke se izvrsil pri startuvanje na kontekstot; go povikuvame uste dva pati
        sampleWorkshops.run(new DefaultApplicationArguments());
        sampleWorkshops.run(new DefaultApplicationArguments());

        assertThat(workshops.findByCode("JAVA-01")).hasValueSatisfying(workshop -> {
            assertThat(workshop.isOpen()).isTrue();
            assertThat(workshop.getTitle()).isEqualTo("Java Workshop");
        });
        assertThat(workshops.findByCode("CLOSED-01")).hasValueSatisfying(workshop ->
                assertThat(workshop.isOpen()).isFalse());
        // findByCode bi frlil izuzetok (IncorrectResultSize) ako ima duplikat; dopolnitelno brojime
        assertThat(workshops.findAll().stream().filter(w -> "JAVA-01".equals(w.getCode())).count()).isEqualTo(1);
        assertThat(workshops.findAll().stream().filter(w -> "CLOSED-01".equals(w.getCode())).count()).isEqualTo(1);
    }
}
