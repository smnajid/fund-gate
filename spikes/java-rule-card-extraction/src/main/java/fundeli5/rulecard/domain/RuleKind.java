package fundeli5.rulecard.domain;

/**
 * The fixed set of typed rule kinds, plus Unmodelled Restriction for anything that fits none of them.
 * Fund Registration is drafted alongside so the extractor never files a selling restriction as Unmodelled
 * (ADR 0003); {@link RuleCard#draft} keeps it out of the card's rules.
 */
public enum RuleKind {
    INVESTOR_CATEGORY,
    US_PERSON_EXCLUSION,
    MINIMUM_INITIAL_SUBSCRIPTION,
    MANDATE_ONLY_CLASS,
    KID_REQUIRED_FOR_RETAIL,
    TARGET_MARKET,
    UNMODELLED_RESTRICTION,
    FUND_REGISTRATION;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
