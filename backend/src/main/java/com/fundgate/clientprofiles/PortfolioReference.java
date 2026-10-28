package com.fundgate.clientprofiles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;

/** The bank's identifier of a portfolio: Legal Entity, client number, dossier number. Client-identifying: never sent to an LLM. */
public record PortfolioReference(@JsonValue String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{3}\\d{8}");

    @JsonCreator
    public PortfolioReference {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a Portfolio Reference: " + value);
        }
    }

    /** The first three letters: the Legal Entity holding the portfolio. */
    public String legalEntity() {
        return value.substring(0, 3);
    }

    @Override
    public String toString() {
        return value;
    }
}
