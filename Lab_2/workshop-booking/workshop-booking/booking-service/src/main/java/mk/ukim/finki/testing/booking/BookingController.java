package mk.ukim.finki.testing.booking;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/registrations")
public class BookingController {
    private final BookingService service;
    public BookingController(BookingService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<RegistrationView> register(@RequestBody BookingRequest request) {
        RegistrationView result = service.register(request);
        return ResponseEntity.created(URI.create("/registrations/" + result.reference())).body(result);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<RegistrationView>> batch(@RequestBody List<BookingRequest> requests) {
        return ResponseEntity.status(201).body(service.registerBatch(requests));
    }

    @GetMapping("/{reference}")
    public RegistrationView find(@PathVariable String reference) { return service.find(reference); }
}
