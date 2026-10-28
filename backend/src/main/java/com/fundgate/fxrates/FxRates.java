package com.fundgate.fxrates;

import java.util.Optional;

/** Out port: the bank's FX table. */
public interface FxRates {

    /** The rate quoted as 1 {@code base} in {@code quote}; empty when the table has no such pair. */
    Optional<FxRate> rate(String base, String quote);
}
