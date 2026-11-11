package mk.ukim.finki.testing.registry;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberController {
    private static final Map<String, Boolean> MEMBERS = Map.of("C-01", true, "C-02", false);

    @GetMapping("/members/{memberId}")
    public ResponseEntity<?> member(@PathVariable String memberId) {
        if (!MEMBERS.containsKey(memberId)) {
            return ResponseEntity.status(404).body(Map.of("code", "MEMBER_NOT_FOUND"));
        }
        return ResponseEntity.ok(new MemberResponse(memberId, MEMBERS.get(memberId)));
    }

    public record MemberResponse(String memberId, boolean eligible) { }
}
