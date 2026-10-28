package com.fundgate.rulecard;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fundgate.bankdata.InvestorType;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The typed parameters of one rule. The LLM only picks the kind and fills these in; one evaluator per kind decides.
 * A card's JSON names the kind as the pipeline's schema does, e.g. {@code "kind": "investor_category"}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Condition.InvestorCategory.class, name = "investor_category"),
        @JsonSubTypes.Type(value = Condition.UsPersonExclusion.class, name = "us_person_exclusion"),
        @JsonSubTypes.Type(value = Condition.MinimumInitialSubscription.class, name = "minimum_initial_subscription"),
        @JsonSubTypes.Type(value = Condition.MandateOnlyClass.class, name = "mandate_only_class"),
        @JsonSubTypes.Type(value = Condition.KidRequiredForRetail.class, name = "kid_required_for_retail"),
        @JsonSubTypes.Type(value = Condition.UnmodelledRestriction.class, name = "unmodelled_restriction"),
        @JsonSubTypes.Type(value = Condition.TargetMarket.class, name = "target_market")})
public sealed interface Condition {

    RuleKind kind();

    /**
     * The investor types the class is open to; qualified and well-informed are derived by the engine. A class can
     * exclude some kinds of investor of an allowed type, e.g. Swiss Life's AM Cap excludes HNWI opt-outs.
     */
    record InvestorCategory(Set<InvestorType> allowed, Set<Excluded> excluded) implements Condition {

        public enum Excluded {
            /** High net worth individuals who opted out under FinSA art. 5(1). */
            HNWI_OPT_OUT,
            /** Qualified investors only through a mandate, under CISA art. 10(3ter). */
            CISA_ART_10_3TER_MANDATE_CLIENT
        }

        public InvestorCategory {
            allowed = Set.copyOf(Objects.requireNonNull(allowed, "allowed"));
            excluded = Set.copyOf(Objects.requireNonNull(excluded, "excluded: an empty list when none"));
        }

        public RuleKind kind() {
            return RuleKind.INVESTOR_CATEGORY;
        }
    }

    /** Which US Person definition the prospectus uses; one US citizen can pass one fund and fail another. */
    record UsPersonExclusion(UsPersonDefinition definition, boolean excludesUsCitizens) implements Condition {

        public UsPersonExclusion {
            Objects.requireNonNull(definition, "definition");
        }

        public enum UsPersonDefinition { REG_S, FATCA, IRC_CITIZENSHIP, MIXED }

        public RuleKind kind() {
            return RuleKind.US_PERSON_EXCLUSION;
        }
    }

    /** The minimum, in the currency the Fund Documentation states it in. */
    record MinimumInitialSubscription(BigDecimal amount, String currency) implements Condition {

        public MinimumInitialSubscription {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
        }

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

        public UnmodelledRestriction {
            Objects.requireNonNull(status, "status");
        }

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
