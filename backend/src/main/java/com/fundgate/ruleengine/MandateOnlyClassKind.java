package com.fundgate.ruleengine;

import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.ClientProfile.Mandate;
import com.fundgate.clientprofiles.ClientProfile.MandateType;
import com.fundgate.clientprofiles.ClientProfile.Manager;
import com.fundgate.clientprofiles.ClientProfile.ManagerKind;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.Locale;
import java.util.Optional;

/** A class reserved to mandates needs a mandate with the manager it names. */
public final class MandateOnlyClassKind implements RuleKindPlugin<MandateOnlyClassKind.MandateOnlyClass> {

    /** A class reserved to mandates, with the manager it names, if any. */
    public record MandateOnlyClass(Optional<String> manager) implements Condition {
    }

    @Override
    public RuleKind kind() {
        return RuleKind.MANDATE_ONLY_CLASS;
    }

    @Override
    public String name() {
        return "mandate_only_class";
    }

    @Override
    public Class<MandateOnlyClass> condition() {
        return MandateOnlyClass.class;
    }

    @Override
    public String prompt() {
        return "- mandate_only_class: the class is reserved to discretionary-mandate clients; manager names the "
                + "manager if the document names one, else null. Draft it only for a class that is reserved.";
    }

    /** The class needs a mandate with the manager it names; the bank's own advice or mandate is not one. */
    @Override
    public Optional<Finding> evaluate(MandateOnlyClass condition, Facts facts, Lookups lookups) {
        Optional<Mandate> mandate = facts.mandate();
        Optional<Manager> manager = mandate.flatMap(Mandate::manager);
        Optional<String> managerName = manager.flatMap(Manager::name);
        Citation[] fields = {facts.mandateTypeField(),
                Facts.field("portfolio.mandate.manager.kind", manager.flatMap(Manager::kind)),
                Facts.field("portfolio.mandate.manager.name", managerName)};
        if (condition.manager().isEmpty()) {
            return Optional.of(facts.mandateType().map(type -> type == MandateType.DISCRETIONARY
                            ? Finding.pass("a discretionary mandate", fields)
                            : Finding.fail("no discretionary mandate", fields))
                    .orElseGet(() -> Finding.review("mandate unknown", fields)));
        }
        String needed = condition.manager().get();
        if (managerName.isPresent()) {
            return Optional.of(managerName.get().toLowerCase(Locale.ROOT).contains(needed.toLowerCase(Locale.ROOT))
                    ? Finding.pass("a mandate with " + needed, fields)
                    : Finding.fail("a mandate with " + managerName.get() + ", not " + needed, fields));
        }
        boolean withTheBank = manager.flatMap(Manager::kind).equals(Optional.of(ManagerKind.BANK))
                || facts.mandateType().filter(type -> type != MandateType.DISCRETIONARY).isPresent();
        if (withTheBank) {
            return Optional.of(Finding.fail("no mandate with " + needed
                    + ": the portfolio is advised or managed by the bank", fields));
        }
        return Optional.of(Finding.review("whether the portfolio has a mandate with " + needed, fields));
    }
}
