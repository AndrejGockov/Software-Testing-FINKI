package mk.ukim.finki.testing.booking;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingApiIT extends AbstractBookingIT {

    @Autowired
    private MockMvc mvc;

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // --- Uspesno kreiranje + dogovor na odgovorot ---

    @Test
    void testCreateReturns201WithLocationAndContractFields() throws Exception {
        postJson("/registrations", json("REG-01", OPEN, "  Ana  ", "Ana@Example.com", "C-01", 2))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/registrations/REG-01")) // relativen Location
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.reference").value("REG-01"))
                .andExpect(jsonPath("$.workshopCode").value(OPEN))
                .andExpect(jsonPath("$.attendee").value("Ana"))            // stripuvano
                .andExpect(jsonPath("$.email").value("ana@example.com"))   // mali bukvi
                .andExpect(jsonPath("$.memberId").value("C-01"))
                .andExpect(jsonPath("$.tickets").isNumber())
                .andExpect(jsonPath("$.tickets").value(2));

        // Sostojba vo bazata
        Registration stored = registrations.findByReference("REG-01").orElseThrow();
        assertThat(stored.getAttendee()).isEqualTo("Ana");
        assertThat(stored.getEmail()).isEqualTo("ana@example.com");
        assertThat(stored.getTickets()).isEqualTo(2);
        assertThat(registrations.count()).isEqualTo(1);
    }

    @Test
    void testGetReturnsTheSameRegistrationThatWasCreated() throws Exception {
        String created = postJson("/registrations", validJson("REG-02"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.id");

        mvc.perform(get("/registrations/{reference}", "REG-02"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.reference").value("REG-02"))
                .andExpect(jsonPath("$.workshopCode").value(OPEN))
                .andExpect(jsonPath("$.attendee").isString())
                .andExpect(jsonPath("$.email").isString())
                .andExpect(jsonPath("$.memberId").isString())
                .andExpect(jsonPath("$.tickets").isNumber());
    }

    @Test
    void testGetUnknownReferenceReturns404() throws Exception {
        mvc.perform(get("/registrations/{reference}", "NO-SUCH-REF"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));
    }

    // --- Nevalidni vlezovi -> 400 INVALID_INPUT, nikakov red, nikakov povik kon registry ---

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("prazen objekt {}", "{}"),
                Arguments.of("tickets = 0", json("REG-01", OPEN, "Ana", "a@example.com", "C-01", 0)),
                Arguments.of("tickets = 6", json("REG-01", OPEN, "Ana", "a@example.com", "C-01", 6)),
                Arguments.of("tickets = null", json("REG-01", OPEN, "Ana", "a@example.com", "C-01", "null")),
                Arguments.of("tickets e tekst", json("REG-01", OPEN, "Ana", "a@example.com", "C-01", "\"abc\"")),
                Arguments.of("nevaliden email", json("REG-01", OPEN, "Ana", "not-an-email", "C-01", 1)),
                Arguments.of("email so prazno mesto", json("REG-01", OPEN, "Ana", " a@example.com", "C-01", 1)),
                Arguments.of("prazen attendee", json("REG-01", OPEN, "   ", "a@example.com", "C-01", 1)),
                Arguments.of("referenca so nedozvolen znak", json("REG_01", OPEN, "Ana", "a@example.com", "C-01", 1)),
                Arguments.of("nepotpoln JSON (malformed)", "{\"reference\":"),
                Arguments.of("ne e JSON", "ova ne e json")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void testInvalidInputReturns400AndChangesNothing(String name, String body) throws Exception {
        postJson("/registrations", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(registrations.count()).isZero();
        assertThat(REGISTRY.requests()).isEmpty();
    }

    // --- Rabotilnica ---

    @Test
    void testUnknownWorkshopReturns404() throws Exception {
        postJson("/registrations", json("REG-01", "IT-NOPE", "Ana", "a@example.com", "C-01", 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHOP_NOT_FOUND"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testClosedWorkshopReturns409() throws Exception {
        postJson("/registrations", json("REG-01", CLOSED, "Ana", "a@example.com", "C-01", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHOP_CLOSED"));

        assertThat(registrations.count()).isZero();
    }

    // --- Registry -> HTTP statusi ---

    @Test
    void testIneligibleMemberReturns422() throws Exception {
        postJson("/registrations", json("REG-01", OPEN, "Ana", "a@example.com", "C-02", 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMBER_INELIGIBLE"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testUnknownMemberReturns422MemberNotFound() throws Exception {
        postJson("/registrations", json("REG-01", OPEN, "Ana", "a@example.com", "C-99", 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testRegistryConnectionDropReturns503() throws Exception {
        REGISTRY.dropConnection();

        postJson("/registrations", validJson("REG-01"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REGISTRY_UNAVAILABLE"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testRegistryTimeoutReturns503() throws Exception {
        REGISTRY.delayResponse(); // kasni podolgo od registry.read-timeout-ms

        postJson("/registrations", validJson("REG-01"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REGISTRY_UNAVAILABLE"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testIncompatibleRegistryResponseReturns502() throws Exception {
        REGISTRY.respondJson(200, "{\"memberId\":\"C-01\",\"eligible\":\"true\"}");

        postJson("/registrations", validJson("REG-01"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("REGISTRY_CONTRACT_ERROR"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testRegistryServerErrorReturns502() throws Exception {
        REGISTRY.respondJson(500, "{\"code\":\"BOOM\"}");

        postJson("/registrations", validJson("REG-01"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("REGISTRY_CONTRACT_ERROR"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testExtraRegistryFieldsAreTolerated() throws Exception {
        REGISTRY.respondJson(200, "{\"memberId\":\"C-01\",\"eligible\":true,\"newField\":123}");

        postJson("/registrations", validJson("REG-01")).andExpect(status().isCreated());

        assertThat(registrations.count()).isEqualTo(1);
    }

    // --- Duplikati -> 409 DATA_CONFLICT (greska od bazata) ---

    @Test
    void testDuplicateReferenceReturns409AndKeepsExistingRow() throws Exception {
        postJson("/registrations", json("DUP-1", OPEN, "Ana", "ana@example.com", "C-01", 2))
                .andExpect(status().isCreated());

        postJson("/registrations", json("DUP-1", OPEN, "Boris", "boris@example.com", "C-01", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

        assertThat(registrations.count()).isEqualTo(1);
        Registration stored = registrations.findByReference("DUP-1").orElseThrow();
        assertThat(stored.getAttendee()).isEqualTo("Ana");
        assertThat(stored.getEmail()).isEqualTo("ana@example.com");
        assertThat(stored.getTickets()).isEqualTo(2);
    }

    @Test
    void testSameEmailInSameWorkshopReturns409EvenWithDifferentCase() throws Exception {
        postJson("/registrations", json("E-1", OPEN, "Ana", "ana@example.com", "C-01", 1))
                .andExpect(status().isCreated());

        postJson("/registrations", json("E-2", OPEN, "Ana", "ANA@Example.COM", "C-01", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

        assertThat(registrations.count()).isEqualTo(1);
        assertThat(registrations.findByReference("E-2")).isEmpty();
    }

    @Test
    void testSameEmailInDifferentWorkshopsIsAllowed() throws Exception {
        workshops.save(new Workshop("IT-OPEN-2", "Second open workshop", true));
        postJson("/registrations", json("E-1", OPEN, "Ana", "ana@example.com", "C-01", 1))
                .andExpect(status().isCreated());

        postJson("/registrations", json("E-2", "IT-OPEN-2", "Ana", "ana@example.com", "C-01", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workshopCode").value("IT-OPEN-2"));

        assertThat(registrations.count()).isEqualTo(2);
    }

    // --- Batch ---

    @Test
    void testBatchReturns201WithObjectsInInputOrderAndNoLocationHeader() throws Exception {
        String body = "[" + validJson("BA-1") + "," + validJson("BA-2") + "," + validJson("BA-3") + "]";

        postJson("/registrations/batch", body)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].reference").value("BA-1"))
                .andExpect(jsonPath("$[1].reference").value("BA-2"))
                .andExpect(jsonPath("$[2].reference").value("BA-3"))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[2].tickets").value(2));

        assertThat(registrations.count()).isEqualTo(3);
    }

    @Test
    void testBatchIsAtomicOverHttp() throws Exception {
        String body = "[" + validJson("AT-1") + ","
                + json("AT-2", OPEN, "Ana", "at-2@example.com", "C-02", 1) + "]"; // vtorata: neprikladen clen

        postJson("/registrations/batch", body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMBER_INELIGIBLE"));

        assertThat(registrations.count()).isZero();
        assertThat(registrations.findByReference("AT-1")).isEmpty();
    }

    @Test
    void testBatchDatabaseConflictReturns409AndRollsBack() throws Exception {
        String body = "[" + json("SAME", OPEN, "Ana", "one@example.com", "C-01", 1) + ","
                + json("SAME", OPEN, "Boris", "two@example.com", "C-01", 1) + "]";

        postJson("/registrations/batch", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

        assertThat(registrations.count()).isZero();
    }

    @Test
    void testEmptyBatchReturns400() throws Exception {
        postJson("/registrations/batch", "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void testBatchOfElevenReturns400AndChangesNothing() throws Exception {
        String body = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> validJson("ELV-" + i))
                .collect(Collectors.joining(",", "[", "]"));

        postJson("/registrations/batch", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(registrations.count()).isZero();
        assertThat(REGISTRY.requests()).isEmpty();
    }

    @Test
    void testBatchWithNullElementReturns400() throws Exception {
        postJson("/registrations/batch", "[null]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void testMalformedBatchJsonReturns400() throws Exception {
        postJson("/registrations/batch", "[{\"reference\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
