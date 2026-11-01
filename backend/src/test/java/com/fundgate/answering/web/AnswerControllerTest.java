package com.fundgate.answering.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.AdaptersConfiguration;
import com.fundgate.answering.AnsweringConfiguration;
import com.fundgate.checkrecord.CheckRecordConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** The dealer screen's one call: a pasted email in, the Verdicts and the reply out (issue #57). */
// The platform reads its own frozen fixtures, never the demo content the team rewrites (#80)
@WebMvcTest(value = AnswerController.class, properties = "fundeli5.fixtures.dir=src/test/resources/fixtures")
@Import({AdaptersConfiguration.class, AnsweringConfiguration.class, CheckRecordConfiguration.class})
class AnswerControllerTest {

    private static final String ANNAS_EMAIL = """
            Subject: Subscription Nordea Global Climate - LOC90000101, LOC90000102

            Hi desk, the client would like to put EUR 80,000 into the Nordea Global Climate fund, institutional class,
            from portfolios LOC90000101 and LOC90000102. Can you confirm they can subscribe?
            """;

    @Autowired
    MockMvcTester mvc;

    @Test
    void annas_email_posted_gives_back_a_verdict_per_portfolio_and_a_reply() {
        var response = post(Map.of("email", ANNAS_EMAIL));

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.swaps[*].portfolioRef")
                .asArray().containsExactly("LOC90000101", "LOC90000102");
        assertThat(response).bodyJson().extractingPath("$.checks[*].portfolioRef")
                .asArray().containsExactly("LOC90000101", "LOC90000102");
        assertThat(response).bodyJson().extractingPath("$.checks[*].shareClass")
                .asArray().containsOnly("LU0348927095");
        // Rows 1 and 26 of the answer key: the advisory portfolio is not institutional; the stub Request Parser
        // names no amount, so the discretionary mandate's minimum needs review until the parser is built (#61)
        assertThat(response).bodyJson().extractingPath("$.checks[*].verdict")
                .asArray().containsExactly("NOT_ELIGIBLE", "NEEDS_REVIEW");
        assertThat(response).bodyJson().extractingPath("$.reply.text")
                .asString().contains("LOC90000101", "LOC90000102");
    }

    @Test
    void an_email_the_privacy_gate_refuses_gives_back_the_refusal_and_no_check() {
        var response = post(Map.of("email", "Hi desk, can LOC90009999 subscribe EUR 80,000?"));

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.refusal.found").asArray().containsExactly("LOC90009999");
        assertThat(response).bodyJson().extractingPath("$.checks").asArray().isEmpty();
        assertThat(response).bodyJson().extractingPath("$.reply").isNull();
    }

    @Test
    void a_body_with_no_email_is_a_bad_request() {
        assertThat(post(Map.of())).hasStatus(400);
    }

    private MvcTestResult post(Map<String, String> body) {
        return mvc.post().uri("/api/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(body))
                .exchange();
    }
}
