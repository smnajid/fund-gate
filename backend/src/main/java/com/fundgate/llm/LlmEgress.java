package com.fundgate.llm;

/** The only code that calls the model (ADR 0004). Every LLM call of both runtimes goes through it. */
public interface LlmEgress {

    LlmResponse complete(LlmRequest request);
}
