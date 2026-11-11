package mk.ukim.finki.testing.booking;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(BookingFailure.class)
    public ResponseEntity<Map<String, String>> domain(BookingFailure failure) {
        return ResponseEntity.status(failure.getStatus()).body(Map.of("code", failure.getCode()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> duplicate(DataIntegrityViolationException failure) {
        return ResponseEntity.status(409).body(Map.of("code", "DATA_CONFLICT"));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> malformed(HttpMessageNotReadableException failure) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT"));
    }
}
