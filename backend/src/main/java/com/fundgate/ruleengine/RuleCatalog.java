package com.fundgate.ruleengine;

import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The kinds of rule a Rule Card may hold, one plug-in each (issue #79). Fund Registration is an input, not on the card. */
public final class RuleCatalog {

    static final List<RuleKindPlugin<?>> KINDS = List.of(
            new InvestorCategoryKind(),
            new UsPersonExclusionKind(),
            new MinimumInitialSubscriptionKind(),
            new MandateOnlyClassKind(),
            new KidRequiredForRetailKind(),
            new UnmodelledRestrictionKind(),
            new TargetMarketKind());

    private RuleCatalog() {
    }

    /** Each kind's name in a card's JSON, with its condition: what the reader of the cards registers. */
    public static Map<String, Class<? extends Condition>> conditionTypes() {
        Map<String, Class<? extends Condition>> types = new LinkedHashMap<>();
        KINDS.forEach(plugin -> types.put(plugin.name(), plugin.condition()));
        return types;
    }

    static RuleKindPlugin<?> of(Condition condition) {
        return KINDS.stream()
                .filter(plugin -> plugin.condition().isInstance(condition))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No plug-in for " + condition.getClass().getName()));
    }

    static Optional<RuleKindPlugin<?>> of(RuleKind kind) {
        return KINDS.stream().filter(plugin -> plugin.kind() == kind).findFirst();
    }
}
