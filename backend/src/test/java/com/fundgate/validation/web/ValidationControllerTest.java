package com.fundgate.validation.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.AdaptersConfiguration;
import com.fundgate.answering.AnsweringConfiguration;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.ruleengine.RuleEngine;
import com.fundgate.shareclasses.Isin;
import com.fundgate.validation.ValidationConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** The validation screen's calls, on the Draft card of Nordea 1 GCE the frozen fixtures hold as version 2 (#66). */
// The platform reads its own frozen fixtures, never the demo content the team rewrites (#80)
@WebMvcTest(value = ValidationController.class, properties = "fundeli5.fixtures.dir=src/test/resources/fixtures")
@Import({AdaptersConfiguration.class, AnsweringConfiguration.class, ValidationConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ValidationControllerTest {

    private static final String GCE_BP = "LU0348926287";
    private static final String DRAFT = "/api/rule-cards/" + GCE_BP + "/versions/2";
    private static final Map<String, String> VALIDATOR = Map.of("validator", "Cynthia");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RuleEngine ruleEngine;

    @Autowired
    ClientProfiles clientProfiles;

    @Test
    void the_draft_shows_the_contradiction_and_the_checked_signals_but_no_confidence() throws Exception {
        var response = mvc.get().uri(DRAFT).exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("DRAFT");
        assertThat(response).bodyJson().extractingPath("$.contradictions[*].kind")
                .asArray().containsExactly("investor_category");
        assertThat(response).bodyJson().extractingPath("$.contradictions[0].shareClass").isEqualTo("LU0348927095");
        assertThat(response).bodyJson().extractingPath("$.readyToValidate").isEqualTo(false);
        assertThat(response).bodyJson().extractingPath("$.rules[0].quoteFoundOnPage").isEqualTo(true);
        assertThat(response).bodyJson().extractingPath("$.rules[0].citations[0].documentType").isEqualTo("PROSPECTUS");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("confidence");
    }

    @Test
    void a_card_with_an_open_contradiction_cannot_be_validated() {
        assertThat(post(DRAFT + "/validate", VALIDATOR)).hasStatus(409);
    }

    @Test
    void once_every_rule_is_reviewed_the_validated_card_is_the_one_the_engine_reads() {
        assertThat(ruleEngine.check(request(), profile()).justification().notices())
                .as("version 1 decides while version 2 is a Draft")
                .doesNotContain("Rule Card not validated");
        List<Integer> contradiction = List.of(5, 6);
        // Rule 5 cites the prospectus, rule 6 the KID: follow the prospectus
        assertThat(post(DRAFT + "/rules/" + contradiction.getFirst() + "/follow", VALIDATOR)).hasStatusOk();
        for (int rule = 0; rule < 8; rule++) {
            if (!contradiction.contains(rule)) {
                assertThat(post(DRAFT + "/rules/" + rule + "/accept", VALIDATOR)).hasStatusOk();
            }
        }

        var validated = post(DRAFT + "/validate", VALIDATOR);

        assertThat(validated).hasStatusOk();
        assertThat(validated).bodyJson().extractingPath("$.validator").isEqualTo("Cynthia");
        assertThat(validated).bodyJson().extractingPath("$.inForce").isEqualTo(true);
        assertThat(validated).bodyJson().extractingPath("$.rules[6].status").isEqualTo("REJECTED");
        assertThat(post(DRAFT + "/rules/0/accept", VALIDATOR)).as("a Validated version is never edited")
                .hasStatus(409);
    }

    @Test
    void marking_the_reject_or_close_clause_noted_is_kept_with_who() {
        var noted = post(DRAFT + "/rules/2/correct", Map.of("validator", "Cynthia", "condition",
                Map.of("kind", "unmodelled_restriction", "status", "noted", "notedReason", "No condition on the client")));

        assertThat(noted).hasStatusOk();
        assertThat(noted).bodyJson().extractingPath("$.rules[2].status").isEqualTo("CORRECTED");
        assertThat(noted).bodyJson().extractingPath("$.rules[2].condition.status").isEqualTo("NOTED");
        assertThat(noted).bodyJson().extractingPath("$.rules[2].by").isEqualTo("Cynthia");
    }

    @Test
    void a_correction_outside_the_rule_card_schema_is_refused() {
        var refused = post(DRAFT + "/rules/2/correct", Map.of("validator", "Cynthia", "condition",
                Map.of("kind", "unmodelled_restriction", "colour", "red")));

        assertThat(refused).hasStatus(400);
    }

    private MvcTestResult post(String uri, Map<String, ?> body) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(body)).exchange();
    }

    private ClientProfile profile() {
        return clientProfiles.clientProfile(new PortfolioReference("LOC90000102"));
    }

    private SubscriptionRequest request() {
        return new SubscriptionRequest(profile().portfolioId(), new Isin(GCE_BP),
                Optional.of(new SubscriptionRequest.Money(new BigDecimal("80000"), "EUR")),
                SubscriptionRequest.ClientInitiated.UNKNOWN);
    }
}
