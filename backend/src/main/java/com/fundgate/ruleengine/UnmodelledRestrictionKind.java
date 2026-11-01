package com.fundgate.ruleengine;

import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.Objects;
import java.util.Optional;

/** A restriction that fits no other kind: Blocking gives Review; Noted passes (ADR 0003). */
public final class UnmodelledRestrictionKind
        implements RuleKindPlugin<UnmodelledRestrictionKind.UnmodelledRestriction> {

    /** A restriction that fits no kind. Blocking gives Review; once a Rule Card Validator marks it Noted, it passes (ADR 0003). */
    public record UnmodelledRestriction(Status status, Optional<String> notedReason) implements Condition {

        public UnmodelledRestriction {
            Objects.requireNonNull(status, "status");
        }

        public enum Status { BLOCKING, NOTED }
    }

    @Override
    public RuleKind kind() {
        return RuleKind.UNMODELLED_RESTRICTION;
    }

    @Override
    public Class<UnmodelledRestriction> condition() {
        return UnmodelledRestriction.class;
    }

    @Override
    public String prompt() {
        return "- unmodelled_restriction: any other restriction on who may subscribe, or on accepting a subscription, "
                + "that fits none of the kinds above; status is blocking.";
    }

    /** ADR 0003: an unreviewed clause never passes silently, so a draft is Blocking whatever the model said. */
    @Override
    public UnmodelledRestriction asDrafted(UnmodelledRestriction drafted) {
        return new UnmodelledRestriction(UnmodelledRestriction.Status.BLOCKING, Optional.empty());
    }

    /** ADR 0003: Blocking gives Review; Noted passes and stays in the Justification with its Citation. */
    @Override
    public Optional<Finding> evaluate(UnmodelledRestriction condition, Facts facts, Lookups lookups) {
        return Optional.of(switch (condition.status()) {
            case BLOCKING -> Finding.review("Blocking: a restriction the engine cannot check");
            case NOTED -> Finding.pass("Noted" + condition.notedReason().map(reason -> ": " + reason).orElse(""));
        });
    }

    /** Each restriction is its own clause: all are shown, and a class's clauses add to the fund's. */
    @Override
    public boolean accumulates() {
        return true;
    }
}
