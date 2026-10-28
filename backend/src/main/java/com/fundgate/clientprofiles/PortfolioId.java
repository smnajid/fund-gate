package com.fundgate.clientprofiles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/** The pseudonym of a portfolio, one to one with its Portfolio Reference. May be sent to an LLM. */
public record PortfolioId(@JsonValue UUID value) {

    @JsonCreator
    public PortfolioId {
        Objects.requireNonNull(value, "Portfolio Id");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
