package com.fundgate.privacygate;

import java.util.List;
import java.util.Objects;

/**
 * The Gate failed closed (ADR 0004): what it found that may not reach an LLM, e.g. an unknown Portfolio Reference or a
 * client name. Shown to the Fund Dealer only, who then enters the request by hand.
 */
public record GateRefusal(String reason, List<String> found) implements GateResult {

    public GateRefusal {
        Objects.requireNonNull(reason, "reason");
        found = List.copyOf(found);
    }
}
