package mk.ukim.finki.testing.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "registrations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_registration_reference", columnNames = "reference"),
    @UniqueConstraint(name = "uk_workshop_email", columnNames = {"workshop_id", "email"})
})
public class Registration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String reference;
    @Column(nullable = false, length = 100)
    private String attendee;
    @Column(nullable = false, length = 100)
    private String email;
    @Column(nullable = false, length = 32)
    private String memberId;
    @Column(nullable = false)
    private int tickets;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id", nullable = false)
    private Workshop workshop;

    protected Registration() { }
    public Registration(String reference, String attendee, String email, String memberId, int tickets, Workshop workshop) {
        this.reference = reference; this.attendee = attendee; this.email = email;
        this.memberId = memberId; this.tickets = tickets; this.workshop = workshop;
    }
    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getAttendee() { return attendee; }
    public String getEmail() { return email; }
    public String getMemberId() { return memberId; }
    public int getTickets() { return tickets; }
    public Workshop getWorkshop() { return workshop; }
}
