package com.fundgate.privacygate;

import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An email the Privacy Gate let through: its text with Portfolio Ids in place of Portfolio References, and the
 * swaps it made, in the order the email names them, so the references can be put back locally after the LLM call.
 */
public record GatedEmail(String text, Map<PortfolioId, PortfolioReference> swaps) implements GateResult {

    public GatedEmail {
        Objects.requireNonNull(text, "text");
        swaps = Collections.unmodifiableMap(new LinkedHashMap<>(swaps));
    }

    /** The email as the Request Parser and Reply Drafter see it: without the swap table. */
    public PseudonymisedEmail pseudonymised() {
        return new PseudonymisedEmail(text, List.copyOf(swaps.keySet()));
    }

    /** Puts the Portfolio References back in text written from this email, e.g. the drafted reply. */
    public String restore(String text) {
        String restored = text;
        for (Map.Entry<PortfolioId, PortfolioReference> swap : swaps.entrySet()) {
            restored = restored.replace(swap.getKey().toString(), swap.getValue().value());
        }
        return restored;
    }
}
