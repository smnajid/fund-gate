package com.fundgate.bankdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;

/** An ISO 3166-1 alpha-2 country code. */
public record Country(@JsonValue String code) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}");

    @JsonCreator
    public Country {
        if (code == null || !FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("Not an ISO 3166-1 alpha-2 country: " + code);
        }
    }

    @Override
    public String toString() {
        return code;
    }
}
