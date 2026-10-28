package com.fundgate.llm;

import java.util.Objects;

/** One call to the model. On the online side, the prompt is Privacy Gate output: Portfolio Ids, never client identity (ADR 0004). */
public record LlmRequest(String model, String system, String prompt) {

    public LlmRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(prompt, "prompt");
    }
}
