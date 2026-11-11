package mk.ukim.finki.testing.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Integracija (provider strana na dogovorot): MemberController vo cela Spring aplikacija (ruting + JSON serijalizacija).
// Realno: kontrolerot, Spring MVC, Jackson serijalizacijata (se proveruvaat tipovite na JSON polinjata).
// Zamena: nema (provider-ot nema zavisnosti - nema baza ni drugi servisi).
//
// consumer(HttpMembershipGateway) bara memberId i eligible
// Ovie testovi garantiraat deka VISTINSKIOT provider go isprakja tocno toj oblik
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberProviderContractIT {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "{0} -> eligible={1}")
    @CsvSource({"C-01, true", "C-02, false"})
    void testKnownMemberReturns200WithContractFields(String memberId, boolean eligible) throws Exception {
        mvc.perform(get("/members/{memberId}", memberId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.memberId").isString())
                .andExpect(jsonPath("$.memberId").value(memberId))
                .andExpect(jsonPath("$.eligible").isBoolean()) // vistinski boolean, ne string "true"
                .andExpect(jsonPath("$.eligible").value(eligible));
    }

    @Test
    void testKnownMemberResponseHasExactlyDocumentedBody() throws Exception {
        mvc.perform(get("/members/C-01"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"memberId\":\"C-01\",\"eligible\":true}", true)); // strict
    }

    @ParameterizedTest
    @ValueSource(strings = {"C-99", "c-01", "UNKNOWN"})
    void testUnknownMemberReturns404WithCode(String memberId) throws Exception {
        // "c-01" (mali bukvi) e razlicen ID od "C-01" - sporedbata e tocna
        mvc.perform(get("/members/{memberId}", memberId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void testRepeatedCallsReturnTheSameAnswerBecauseLookupIsReadOnly() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/members/C-02"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eligible").value(false));
        }
    }
}
