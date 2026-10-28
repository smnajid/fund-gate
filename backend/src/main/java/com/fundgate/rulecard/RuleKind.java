package com.fundgate.rulecard;

/** The fixed set of rules an Eligibility Check evaluates (issue #6). Fund Registration is an input, not on the card. */
public enum RuleKind {
    INVESTOR_CATEGORY,
    US_PERSON_EXCLUSION,
    MINIMUM_INITIAL_SUBSCRIPTION,
    MANDATE_ONLY_CLASS,
    KID_REQUIRED_FOR_RETAIL,
    UNMODELLED_RESTRICTION,
    TARGET_MARKET,
    FUND_REGISTRATION
}
