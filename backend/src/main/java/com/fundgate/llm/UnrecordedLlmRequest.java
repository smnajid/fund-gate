package com.fundgate.llm;

/** Replay mode was asked something nobody recorded. It fails instead of calling the model. */
public class UnrecordedLlmRequest extends RuntimeException {

    public UnrecordedLlmRequest(String message) {
        super(message);
    }
}
