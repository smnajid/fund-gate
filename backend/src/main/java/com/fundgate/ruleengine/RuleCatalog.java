package com.fundgate.ruleengine;

import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The kinds of rule a Rule Card may hold, one plug-in each (issue #79). Fund Registration is an input, not on the card. */
public final class RuleCatalog {

    static final List<RuleKindPlugin<?>> KINDS = List.of(
            new InvestorCategoryKind(),
            new UsPersonExclusionKind(),
            new MinimumInitialSubscriptionKind(),
            new MandateOnlyClassKind(),
            new KidRequiredForRetailKind(),
            new TargetMarketKind(),
            // Last: its prompt line says "fits none of the kinds above"
            new UnmodelledRestrictionKind());

    private RuleCatalog() {
    }

    /** Each kind's name in a card's JSON, with its condition: what the reader of the cards registers. */
    public static Map<String, Class<? extends Condition>> conditionTypes() {
        Map<String, Class<? extends Condition>> types = new LinkedHashMap<>();
        KINDS.forEach(plugin -> types.put(plugin.name(), plugin.condition()));
        return types;
    }

    /** Each kind's line of the drafting prompt, in catalog order: what the Rule Card pipeline asks the model for. */
    public static String draftingPrompt() {
        return KINDS.stream().map(RuleKindPlugin::prompt).collect(Collectors.joining("\n"));
    }

        /** The condition a Draft card proposes for what the model drafted, as the kind's plug-in says. */
    public static Condition asDrafted(Condition drafted) {
        return asDrafted(of(drafted), drafted);
    }

    private static <C extends Condition> C asDrafted(RuleKindPlugin<C> plugin, Condition drafted) {
        return plugin.asDrafted(plugin.condition().cast(drafted));
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
