package mk.ukim.finki.testing.booking;

public record RegistrationView(Long id, String reference, String workshopCode,
                               String attendee, String email, String memberId, int tickets) {
    public static RegistrationView from(Registration r) {
        return new RegistrationView(r.getId(), r.getReference(), r.getWorkshop().getCode(),
                r.getAttendee(), r.getEmail(), r.getMemberId(), r.getTickets());
    }
}
