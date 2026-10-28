package com.fundgate.rulecard;

import com.fundgate.bankdata.InvestorType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/** The typed parameters of one rule. The LLM only picks the kind and fills these in; one evaluator per kind decides. */
public sealed interface Condition {

    RuleKind kind();

    /** The investor types the class is open to; qualified and well-informed are derived by the engine. */
    record InvestorCategory(Set<InvestorType> allowed) implements Condition {

        public InvestorCategory {
            allowed = Set.copyOf(allowed);
        }

        public RuleKind kind() {
            return RuleKind.INVESTOR_CATEGORY;
        }
    }

    /** Which US Person definition the prospectus uses; one US citizen can pass one fund and fail another. */
    record UsPersonExclusion(UsPersonDefinition definition, boolean excludesUsCitizens) implements Condition {

        public enum UsPersonDefinition { REG_S, FATCA, IRC_CITIZENSHIP, MIXED }

        public RuleKind kind() {
            return RuleKind.US_PERSON_EXCLUSION;
        }
    }

    /** The minimum, in the currency the Fund Documentation states it in. */
    record MinimumInitialSubscription(BigDecimal amount, String currency) implements Condition {

        public RuleKind kind() {
            return RuleKind.MINIMUM_INITIAL_SUBSCRIPTION;
        }
    }

    /** A class reserved to mandates, with the manager it names, if any. */
    record MandateOnlyClass(Optional<String> manager) implements Condition {

        public RuleKind kind() {
            return RuleKind.MANDATE_ONLY_CLASS;
        }
    }

    record KidRequiredForRetail(boolean kidAvailable) implements Condition {

        public RuleKind kind() {
            return RuleKind.KID_REQUIRED_FOR_RETAIL;
        }
    }

    /** A restriction that fits no kind. Blocking gives Review; once a Rule Card Validator marks it Noted, it passes (ADR 0003). */
    record UnmodelledRestriction(Status status, Optional<String> notedReason) implements Condition {

        public enum Status { BLOCKING, NOTED }

        public RuleKind kind() {
            return RuleKind.UNMODELLED_RESTRICTION;
        }
    }

    /** One Target Market dimension, matched through the Scale Mapping. */
    record TargetMarket(Dimension dimension, Polarity polarity, String value) implements Condition {

        public enum Dimension { CLIENT_TYPE, KNOWLEDGE_AND_EXPERIENCE, ABILITY_TO_BEAR_LOSSES, RISK_SRI, HORIZON_YEARS }

        /** Positive: who the fund is for. Negative: who it is not for. */
        public enum Polarity { POSITIVE, NEGATIVE }

        public RuleKind kind() {
            return RuleKind.TARGET_MARKET;
        }
    }
}
