package com.fundgate.checkrecord;

import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.Justification;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.Verdict;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything one Eligibility Check read and produced, written once and never changed (architecture.md, Audit).
 * It keeps the Portfolio Reference; anything sent to an LLM carried only the Portfolio Id.
 */
public record CheckRecord(
        UUID checkId,
        Instant checkedAt,
        String dealer,
        PortfolioReference portfolioRef,
        SubscriptionRequest request,
        /** Empty when the class has no Rule Card. */
        Optional<RuleCardVersion> ruleCard,
        String scaleMappingVersion,
        String engineVersion,
        /** The Client Profile fields the check read, with their values at check time. */
        List<Citation.ClientProfileField> clientProfileSnapshot,
        Verdict verdict,
        Justification justification,
        List<LlmCall> llmCalls) {

    public CheckRecord {
        clientProfileSnapshot = List.copyOf(clientProfileSnapshot);
        llmCalls = List.copyOf(llmCalls);
    }

    public record RuleCardVersion(Optional<String> fundId, String fundName, int version) {
    }

    /** One LLM call: exactly what went out, after the Privacy Gate, and what came back. */
    public record LlmCall(
            String purpose,
            LlmRequest gatedRequest,
            /** The allow-listed Client Profile fields the call carried. */
            List<String> fieldsSent,
            LlmResponse response,
            Instant calledAt) {

        public LlmCall {
            fieldsSent = List.copyOf(fieldsSent);
        }
    }
}
