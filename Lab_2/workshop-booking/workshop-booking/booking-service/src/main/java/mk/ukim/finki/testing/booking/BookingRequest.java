package mk.ukim.finki.testing.booking;

import jakarta.validation.constraints.*;

public record BookingRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,32}") String reference,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,32}") String workshopCode,
    @NotBlank @Size(max = 100) String attendee,
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,32}") String memberId,
    @NotNull @Min(1) @Max(5) Integer tickets
) { }
