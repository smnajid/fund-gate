package com.fundgate.llm;

/** The model's answer, with the model id that gave it, for the Check Record. */
public record LlmResponse(String model, String text) {
}
