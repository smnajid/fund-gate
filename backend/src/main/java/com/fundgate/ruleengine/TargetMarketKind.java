package com.fundgate.ruleengine;

import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.Optional;

/** One Target Market dimension, matched through the Scale Mapping (#59). */
public final class TargetMarketKind implements RuleKindPlugin<TargetMarketKind.TargetMarket> {

    /** One Target Market dimension, matched through the Scale Mapping. */
    public record TargetMarket(Dimension dimension, Polarity polarity, String value) implements Condition {

        public enum Dimension { CLIENT_TYPE, KNOWLEDGE_AND_EXPERIENCE, ABILITY_TO_BEAR_LOSSES, RISK_SRI, HORIZON_YEARS }

        /** Positive: who the fund is for. Negative: who it is not for. */
        public enum Polarity { POSITIVE, NEGATIVE }

        public RuleKind kind() {
            return RuleKind.TARGET_MARKET;
        }
    }

    @Override
    public RuleKind kind() {
        return RuleKind.TARGET_MARKET;
    }

    @Override
    public String name() {
        return "target_market";
    }

    @Override
    public Class<TargetMarket> condition() {
        return TargetMarket.class;
    }

    @Override
    public String prompt() {
        return "- target_market: one rule per dimension: client_type (all, retail, professional, institutional), "
                + "knowledge_experience (basic, informed, advanced), loss_bearing (no_loss, limited_loss, full_loss), "
                + "risk_sri and horizon_years (plain numbers); polarity is positive (who the fund is for) or negative "
                + "(who it is not for).";
    }

    /** Empty until the Scale Mapping matches the dimension (#59). */
    @Override
    public Optional<Finding> evaluate(TargetMarket condition, Facts facts, Lookups lookups) {
        return Optional.empty();
    }
}
