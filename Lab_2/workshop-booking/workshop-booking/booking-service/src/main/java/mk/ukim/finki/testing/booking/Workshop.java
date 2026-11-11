package mk.ukim.finki.testing.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "workshops", uniqueConstraints = @UniqueConstraint(name = "uk_workshop_code", columnNames = "code"))
public class Workshop {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String code;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false)
    private boolean open;

    protected Workshop() { }
    public Workshop(String code, String title, boolean open) {
        this.code = code; this.title = title; this.open = open;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public boolean isOpen() { return open; }
}
