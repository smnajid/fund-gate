package com.fundgate.checkrecord;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A field of a Subscription Request the Fund Dealer corrected after the AI read it, recorded beside the Check Record,
 * which keeps what the AI read (screens.md, The request form). Whether a correction re-runs the check is open
 * (07-audit-a-check.feature, {@code @assumed}): recording one runs nothing.
 */
public record DealerCorrection(
        UUID checkId,
        Field field,
        /** What the AI read; empty when it read nothing, e.g. no amount. */
        Optional<String> parsed,
        String corrected,
        String dealer,
        Instant correctedAt) {

    public DealerCorrection {
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(dealer, "dealer");
        Objects.requireNonNull(correctedAt, "correctedAt");
        if (corrected == null || corrected.isBlank()) {
            throw new IllegalArgumentException("A correction needs the corrected value");
        }
    }

    /** The fields of the request form the AI read, so the Fund Dealer may correct them; never the Portfolio Reference. */
    public enum Field { SHARE_CLASS, AMOUNT, CURRENCY, CLIENT_INITIATED }
}
