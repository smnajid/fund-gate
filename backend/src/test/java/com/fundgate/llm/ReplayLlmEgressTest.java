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
    void a_recording_holding_a_portfolio_reference_is_refused() throws IOException {
        // Recorded from an email before the Privacy Gate swapped the reference for its Portfolio Id
        Files.writeString(recordings.resolve("before-the-gate.json"), """
                {
                  "request": { "model": "claude-sonnet-5-5", "system": "Parse.", "prompt": "Please check LOC90000101." },
                  "response": { "model": "claude-sonnet-5-5", "text": "[]" }
                }
                """);

        assertThatThrownBy(() -> new ReplayLlmEgress(recordings))
                .hasMessageContaining("before-the-gate.json")
                .hasMessageContaining("Portfolio Reference")
                .hasMessageNotContaining("90000101");
    }

    @Test
    void two_recordings_of_the_same_request_name_the_files_not_the_prompt() throws IOException {
        String recording = """
                {
                  "request": { "model": "claude-sonnet-5-5", "system": "Parse.", "prompt": "a gated prompt" },
                  "response": { "model": "claude-sonnet-5-5", "text": "[]" }
                }
                """;
        Files.writeString(recordings.resolve("first.json"), recording);
        Files.writeString(recordings.resolve("second.json"), recording);

        assertThatThrownBy(() -> new ReplayLlmEgress(recordings))
                .hasMessageContaining("first.json")
                .hasMessageContaining("second.json")
                .hasMessageNotContaining("a gated prompt");
    }

    @Test
    void a_malformed_recording_names_its_file_not_its_content() throws IOException {
        Files.writeString(recordings.resolve("broken.json"), "{ \"request\": { \"prompt\": \"a gated prompt\" ");

        assertThatThrownBy(() -> new ReplayLlmEgress(recordings))
                .hasMessageContaining("broken.json")
                .hasMessageNotContaining("a gated prompt");
    }

    @Test
    void a_recordings_folder_that_is_not_there_fails_at_startup() {
        assertThatThrownBy(() -> new ReplayLlmEgress(recordings.resolve("no-such-folder")))
                .hasMessageContaining("no-such-folder");
    }

    @Test
    void a_recording_with_a_misspelt_key_is_refused_not_left_unmatched() throws IOException {
        Files.writeString(recordings.resolve("misspelt.json"), """
                {
                  "request": { "model": "claude-sonnet-5-5", "sytem": "Parse.", "prompt": "a gated prompt" },
                  "response": { "model": "claude-sonnet-5-5", "text": "[]" }
                }
                """);

        assertThatThrownBy(() -> new ReplayLlmEgress(recordings)).hasMessageContaining("misspelt.json");
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
