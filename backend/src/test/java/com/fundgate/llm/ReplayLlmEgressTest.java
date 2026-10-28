package com.fundgate.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.llm.replay.ReplayLlmEgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Replay mode serves recorded answers, so rehearsals and the specification make no model call (ADR 0004). */
class ReplayLlmEgressTest {

    private static final LlmRequest PARSE_ANNAS_EMAIL = new LlmRequest(
            "claude-sonnet-5-5",
            "Turn the request into Subscription Requests.",
            "Please check 2b9c1f0e-7d4a-4e11-9b1a-5c3e8f2d6a40 for Nordea 1 GCE BI-EUR, EUR 80,000.");

    @TempDir
    Path recordings;

    @Test
    void a_recorded_request_gets_its_recorded_answer() throws IOException {
        Files.writeString(recordings.resolve("annas-email.json"), """
                {
                  "request": {
                    "model": "claude-sonnet-5-5",
                    "system": "Turn the request into Subscription Requests.",
                    "prompt": "Please check 2b9c1f0e-7d4a-4e11-9b1a-5c3e8f2d6a40 for Nordea 1 GCE BI-EUR, EUR 80,000."
                  },
                  "response": { "model": "claude-sonnet-5-5", "text": "[{\\"isin\\": \\"LU0348927095\\"}]" }
                }
                """);
        LlmEgress egress = new ReplayLlmEgress(recordings);

        LlmResponse response = egress.complete(PARSE_ANNAS_EMAIL);

        assertThat(response.text()).isEqualTo("[{\"isin\": \"LU0348927095\"}]");
        assertThat(response.model()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void a_request_nobody_recorded_fails_loudly_instead_of_calling_the_model() {
        LlmEgress egress = new ReplayLlmEgress(recordings);

        assertThatThrownBy(() -> egress.complete(PARSE_ANNAS_EMAIL))
                .isInstanceOf(UnrecordedLlmRequest.class)
                .hasMessageContaining("claude-sonnet-5-5")
                .hasMessageContaining(recordings.toString());
    }
}
