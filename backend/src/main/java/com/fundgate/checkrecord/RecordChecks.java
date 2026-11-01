package com.fundgate.checkrecord;

import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleCards;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In port: writes the one Check Record of an Eligibility Check that has run, with everything needed to reproduce its
 * Verdict (architecture.md, Audit). The record is new every time: nothing here updates or deletes one.
 */
public class RecordChecks {

    /** Until the Scale Mapping is built and versioned (#59). */
    public static final String SCALE_MAPPING_PENDING = "pending";

    /** Until the Fund Dealer signs in. */
    static final String DEALER = "not signed in";

    private final CheckRecords checkRecords;
    private final RuleCards ruleCards;
    private final String engineVersion;
    private final Clock clock;

    /** The engine version is the application's build: the Rule Engine is fixed code shipped with it (ADR 0002). */
    public RecordChecks(CheckRecords checkRecords, RuleCards ruleCards, String engineVersion, Clock clock) {
        this.checkRecords = checkRecords;
        this.ruleCards = ruleCards;
        this.engineVersion = engineVersion;
        this.clock = clock;
    }

    /**
     * Writes the record and returns its checkId. The llmCalls are every call the email needed, after the Privacy
     * Gate. Throws {@link com.fundgate.bankdata.FundDataUnavailable}.
     */
    public UUID record(PortfolioReference portfolioRef, SubscriptionRequest request, EligibilityResult result,
            List<CheckRecord.LlmCall> llmCalls) {
        UUID checkId = UUID.randomUUID();
        checkRecords.write(new CheckRecord(checkId, clock.instant(), DEALER, portfolioRef, request,
                ruleCardVersion(request), SCALE_MAPPING_PENDING, engineVersion, clientProfileRead(result),
                result.verdict(), result.justification(), llmCalls));
        return checkId;
    }

    /**
     * The card that decides for the class, as the Rule Engine reads it: Rule Card versions are never edited, only
     * superseded, so this is the version the check read unless one was validated in between.
     */
    private Optional<CheckRecord.RuleCardVersion> ruleCardVersion(SubscriptionRequest request) {
        return ruleCards.forShareClass(request.shareClass()).map(RecordChecks::version);
    }

    private static CheckRecord.RuleCardVersion version(RuleCard card) {
        return new CheckRecord.RuleCardVersion(card.fundId(), card.fundName(), card.version());
    }

    /** Every rule outcome cites the Client Profile fields it read, with their values at check time. */
    private static List<Citation.ClientProfileField> clientProfileRead(EligibilityResult result) {
        return result.justification().outcomes().stream()
                .flatMap(outcome -> outcome.citations().stream())
                .filter(Citation.ClientProfileField.class::isInstance)
                .map(Citation.ClientProfileField.class::cast)
                .distinct()
                .toList();
    }
}
