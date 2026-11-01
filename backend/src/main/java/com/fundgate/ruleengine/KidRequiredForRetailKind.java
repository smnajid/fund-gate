package com.fundgate.ruleengine;

import com.fundgate.clientprofiles.ClientProfile.Classification.Category;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.util.Optional;

/** A retail client needs a KID (PRIIPs, FinSA); without one the class is closed to retail clients. */
public final class KidRequiredForRetailKind implements RuleKindPlugin<KidRequiredForRetailKind.KidRequiredForRetail> {

    public record KidRequiredForRetail(boolean kidAvailable) implements Condition {
    }

    @Override
    public RuleKind kind() {
        return RuleKind.KID_REQUIRED_FOR_RETAIL;
    }

    @Override
    public Class<KidRequiredForRetail> condition() {
        return KidRequiredForRetail.class;
    }

    @Override
    public String prompt() {
        return "- kid_required_for_retail: kidAvailable says whether a PRIIPs KID exists for the class.";
    }

    @Override
    public Optional<Finding> evaluate(KidRequiredForRetail condition, Facts facts, Lookups lookups) {
        if (condition.kidAvailable()) {
            return Optional.of(Finding.pass("a KID is available"));
        }
        return Optional.of(facts.category()
                .map(category -> category == Category.RETAIL
                        ? Finding.fail("no KID for a retail client", facts.categoryField())
                        : Finding.pass("no KID needed for a " + Facts.lower(category) + " client", facts.categoryField()))
                .orElseGet(() -> Finding.review("no KID, and the Investor Classification is unknown",
                        facts.categoryField())));
    }
}
