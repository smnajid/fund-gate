package com.fundgate.shareclasses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;

/** The key of a Share Class. Placeholder ISINs start with XX, which no real ISIN uses. */
public record Isin(@JsonValue String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}\\d");

    @JsonCreator
    public Isin {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Not an ISIN: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
