package mk.ukim.finki.testing.booking;

public class BookingFailure extends RuntimeException {
    private final int status;
    private final String code;
    public BookingFailure(int status, String code) { super(code); this.status = status; this.code = code; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
}
