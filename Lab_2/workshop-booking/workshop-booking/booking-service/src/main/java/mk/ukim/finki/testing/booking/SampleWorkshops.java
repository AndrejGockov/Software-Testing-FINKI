package mk.ukim.finki.testing.booking;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SampleWorkshops implements ApplicationRunner {
    private final WorkshopRepository workshops;
    public SampleWorkshops(WorkshopRepository workshops) { this.workshops = workshops; }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (workshops.findByCode("JAVA-01").isEmpty()) { workshops.save(new Workshop("JAVA-01", "Java Workshop", true)); }
        if (workshops.findByCode("CLOSED-01").isEmpty()) { workshops.save(new Workshop("CLOSED-01", "Closed Workshop", false)); }
    }
}
