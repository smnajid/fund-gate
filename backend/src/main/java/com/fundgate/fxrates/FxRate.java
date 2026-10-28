package com.fundgate.fxrates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** One dated rate of the bank's FX table: 1 {@code base} = {@code rate} {@code quote}. */
public record FxRate(String base, String quote, BigDecimal rate, LocalDate date, String source) {

    public FxRate {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(source, "source");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("Not a rate: " + pair() + " " + rate);
        }
    }

    /** As the Citation names it, e.g. EUR/CHF. */
    public String pair() {
        return base + "/" + quote;
    }
}
