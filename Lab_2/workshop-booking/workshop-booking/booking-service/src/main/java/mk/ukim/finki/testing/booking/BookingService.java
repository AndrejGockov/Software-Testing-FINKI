package mk.ukim.finki.testing.booking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingService {
    private final WorkshopRepository workshops;
    private final RegistrationRepository registrations;
    private final MembershipGateway membership;
    private final Validator validator;

    public BookingService(WorkshopRepository workshops, RegistrationRepository registrations,
                          MembershipGateway membership, Validator validator) {
        this.workshops = workshops; this.registrations = registrations;
        this.membership = membership; this.validator = validator;
    }

    public RegistrationView register(BookingRequest request) {
        return RegistrationView.from(create(request));
    }

    public List<RegistrationView> registerBatch(List<BookingRequest> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 10) {
            throw new BookingFailure(400, "INVALID_INPUT");
        }
        List<RegistrationView> result = new ArrayList<>();
        for (BookingRequest request : requests) {
            result.add(RegistrationView.from(create(request)));
        }
        return result;
    }

    private Registration create(BookingRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BookingFailure(400, "INVALID_INPUT");
        }
        Workshop workshop = workshops.findByCode(request.workshopCode())
                .orElseThrow(() -> new BookingFailure(404, "WORKSHOP_NOT_FOUND"));
        if (!workshop.isOpen()) { throw new BookingFailure(409, "WORKSHOP_CLOSED"); }
        if (!membership.isEligible(request.memberId())) {
            throw new BookingFailure(422, "MEMBER_INELIGIBLE");
        }
        Registration registration = new Registration(request.reference(), request.attendee().strip(),
                request.email().toLowerCase(Locale.ROOT), request.memberId(), request.tickets(), workshop);
        return registrations.saveAndFlush(registration);
    }

    @Transactional(readOnly = true)
    public RegistrationView find(String reference) {
        return RegistrationView.from(registrations.findByReference(reference)
                .orElseThrow(() -> new BookingFailure(404, "REGISTRATION_NOT_FOUND")));
    }
}
