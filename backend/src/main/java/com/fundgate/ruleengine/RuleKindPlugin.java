package com.fundgate.ruleengine;

import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.Locale;
import java.util.Optional;

/**
 * One kind of rule on a Rule Card, whole: its condition, how the engine decides it, and how the LLM is asked to draft
 * it (issue #79). Every Hard Restriction, the Unmodelled Restriction and the Target Market are kinds. A kind the
 * business adds is one plug-in, listed in {@link RuleCatalog}, plus its {@link RuleKind} constant, which the API
 * names; nothing else in the engine knows a kind.
 */
interface RuleKindPlugin<C extends Condition> {

    RuleKind kind();

    /** The kind's name in a card's JSON and in the drafting prompt, e.g. investor_category. */
    default String name() {
        return kind().name().toLowerCase(Locale.ROOT);
    }

    Class<C> condition();

    /**
     * The kind's line in the drafting prompt, copied from the pipeline's DraftPrompt (#65, not merged yet), which
     * will assemble the lines in catalog order.
     */
    String prompt();

    /** Fixed code, the card only fills in the parameters. Empty when the engine does not decide this kind yet. */
    Optional<Finding> evaluate(C condition, Facts facts, Lookups lookups);

    /**
     * True when every rule of the kind is shown, and a class-level rule adds to the fund-level ones instead of
     * overriding them. Otherwise the worst finding decides and a class-level rule overrides.
     */
    default boolean accumulates() {
        return false;
    }

    /** What an evaluator may look up beyond the card and the facts. */
    record Lookups(InvestorTypes investorTypes, Money money) {
    }
}
