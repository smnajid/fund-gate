package com.fundgate.answering.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.AdaptersConfiguration;
import com.fundgate.answering.AnsweringConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/** The dealer screen's one call: a pasted email in, the Verdicts and the reply out (issue #57). */
@WebMvcTest(AnswerController.class)
@Import({AdaptersConfiguration.class, AnsweringConfiguration.class})
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
        var response = mvc.post().uri("/api/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(Map.of("email", ANNAS_EMAIL)))
                .exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.swaps[*].portfolioRef")
                .asArray().containsExactly("LOC90000101", "LOC90000102");
        assertThat(response).bodyJson().extractingPath("$.checks[*].portfolioRef")
                .asArray().containsExactly("LOC90000101", "LOC90000102");
        assertThat(response).bodyJson().extractingPath("$.checks[*].shareClass")
                .asArray().containsOnly("LU0348927095");
        assertThat(response).bodyJson().extractingPath("$.checks[*].verdict")
                .asArray().containsOnly("NEEDS_REVIEW");
        assertThat(response).bodyJson().extractingPath("$.reply.text")
                .asString().contains("LOC90000101", "LOC90000102");
    }
}
