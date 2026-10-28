package com.fundgate.rulecard;

import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.shareclasses.Isin;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The validated, structured restrictions for the Share Classes of one fund, each rule cited. Versions are never
 * edited, only superseded. Only a Validated card decides; a Draft card gives Needs Review (ADR 0001).
 */
public record RuleCard(
        String fundName,
        /** The classes this card covers: the join from a Share Class to its card. */
        List<Isin> shareClasses,
        int version,
        Status status,
        List<Rule> rules,
        List<Source> sources,
        Drafting drafting,
        Optional<Validation> validation) {

    public RuleCard {
        shareClasses = List.copyOf(shareClasses);
        rules = List.copyOf(rules);
        sources = List.copyOf(sources);
    }

    public enum Status { DRAFT, VALIDATED }

    /** A document the card was drafted from: what makes "newer edition available" and an auditor's fetch possible. */
    public record Source(
            DocumentId documentId,
            String contentSha256,
            Optional<String> fmdEditionDate,
            Optional<String> pdfEditionDate,
            FundDocument.Confidentiality confidentiality) {
    }

    /** Which model read the documents, and through which endpoint of the egress client. */
    public record Drafting(String model, String endpoint, Instant draftedAt) {
    }

    /** Who validated this version, and when. */
    public record Validation(String validator, Instant validatedAt) {
    }
}
