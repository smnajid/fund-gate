package com.fundgate.ruleengine;

import com.fundgate.bankdata.InvestorType;
import com.fundgate.clientprofiles.ClientProfile.Classification.DeclarationType;
import com.fundgate.eligibility.RuleOutcome.Outcome;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Which investor types the class is open to, following the regime's Investor Classification, mandate and declarations. */
public final class InvestorCategoryKind implements RuleKindPlugin<InvestorCategoryKind.InvestorCategory> {

    /**
     * The investor types the class is open to; qualified and well-informed are derived by the engine. A class can
     * exclude some kinds of investor of an allowed type, e.g. Swiss Life's AM Cap excludes HNWI opt-outs.
     */
    public record InvestorCategory(Set<InvestorType> allowed, Set<Excluded> excluded) implements Condition {

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
    }

    @Override
    public RuleKind kind() {
        return RuleKind.INVESTOR_CATEGORY;
    }

    @Override
    public String name() {
        return "investor_category";
    }

    @Override
    public Class<InvestorCategory> condition() {
        return InvestorCategory.class;
    }

    @Override
    public String prompt() {
        return "- investor_category: which investor categories may buy the class; allowed_categories from all, "
                + "retail, professional, institutional, qualified, well_informed.";
    }

    @Override
    public Optional<Finding> evaluate(InvestorCategory condition, Facts facts, Lookups lookups) {
        Finding allowed = lookups.investorTypes().isAnyOf(condition.allowed(), facts);
        if (allowed.outcome() != Outcome.PASS || condition.excluded().isEmpty()) {
            return Optional.of(allowed);
        }
        List<Finding> exclusions = condition.excluded().stream().sorted().map(excluded -> switch (excluded) {
            case HNWI_OPT_OUT -> facts.declarations().isEmpty()
                    ? Finding.review("declarations unknown: an HNWI opt-out is excluded", facts.declarationsField())
                    : facts.declared(DeclarationType.FINSA_HNWI_OPT_OUT)
                            ? Finding.fail("HNWI opt-outs (FinSA art. 5(1)) are excluded", facts.declarationsField())
                            : Finding.pass("not an HNWI opt-out", facts.declarationsField());
            case CISA_ART_10_3TER_MANDATE_CLIENT -> facts.category().isEmpty() || facts.mandateType().isEmpty()
                    ? Finding.review("Investor Classification or mandate unknown: CISA art. 10(3ter) mandate clients"
                            + " are excluded", facts.categoryField(), facts.mandateTypeField())
                    : InvestorTypes.qualifiedOnlyByMandate(facts)
                    ? Finding.fail("qualified only through a mandate (CISA art. 10(3ter)), which is excluded",
                            facts.categoryField(), facts.mandateTypeField())
                    : Finding.pass("not qualified through a mandate only", facts.categoryField());
        }).toList();
        Finding excludedOrNot = Finding.worst(exclusions);
        return Optional.of(excludedOrNot.outcome() == Outcome.PASS ? allowed : excludedOrNot);
    }
}
