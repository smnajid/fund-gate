package com.fundgate.clientprofiles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;

/** The bank's identifier of a portfolio: Legal Entity, client number, dossier number. Client-identifying: never sent to an LLM. */
public record PortfolioReference(@JsonValue String value) {

    /** The format, also used to refuse text that still holds a reference where only Portfolio Ids may be. */
    public static final Pattern FORMAT = Pattern.compile("[A-Z]{3}\\d{8}");

    @JsonCreator
    public PortfolioReference {
        if (value == null || !FORMAT.matcher(value).matches()) {
            // The input is not echoed: it may be free text holding client data
            throw new IllegalArgumentException("Not a Portfolio Reference: expected 3 letters and 8 digits");
        }
    }

    /** The first three letters: the Legal Entity holding the portfolio. */
    public String legalEntity() {
        return value.substring(0, 3);
    }

    /** Masked, so a reference printed by accident in a log or a prompt shows only its Legal Entity. Use {@link #value()}. */
    @Override
    public String toString() {
        return legalEntity() + "********";
    }
}
