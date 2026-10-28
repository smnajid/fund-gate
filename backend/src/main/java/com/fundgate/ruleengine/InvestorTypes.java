package com.fundgate.ruleengine;

import com.fundgate.bankdata.InvestorType;
import com.fundgate.clientprofiles.ClientProfile.Classification.Category;
import com.fundgate.clientprofiles.ClientProfile.Classification.DeclarationType;
import com.fundgate.clientprofiles.ClientProfile.MandateType;
import com.fundgate.eligibility.SubscriptionRequest;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

/**
 * Whether a client is of an investor type. Qualified (CH) and well-informed (LU) are derived from the Investor
 * Classification, the mandate and the declarations, never stored (issue #2).
 */
class InvestorTypes {

    /** SIF Law art. 2(1): a well-informed investor by written confirmation invests at least EUR 100,000. */
    private static final BigDecimal WELL_INFORMED_MINIMUM_EUR = new BigDecimal("100000");

    private final Money money;

    InvestorTypes(Money money) {
        this.money = money;
    }

    /** One Pass among the types is enough. */
    Finding isAnyOf(Collection<InvestorType> types, Facts facts) {
        return Finding.best(types.stream().sorted().map(type -> is(type, facts)).toList());
    }

    Finding is(InvestorType type, Facts facts) {
        return switch (type) {
            case ALL -> Finding.pass("open to all investors");
            case RETAIL, PROFESSIONAL -> category(type, facts);
            case INSTITUTIONAL -> institutional(facts);
            case QUALIFIED -> qualified(facts);
            case WELL_INFORMED -> wellInformed(facts);
        };
    }

    /** Qualified only through a mandate (CISA art. 10(3ter)): a retail client under a discretionary mandate. */
    static boolean qualifiedOnlyByMandate(Facts facts) {
        return facts.category().equals(Optional.of(Category.RETAIL))
                && facts.mandateType().equals(Optional.of(MandateType.DISCRETIONARY));
    }

    private static Finding category(InvestorType type, Facts facts) {
        if (facts.category().isEmpty()) {
            return Finding.review("Investor Classification unknown", facts.categoryField());
        }
        return facts.category().get().name().equals(type.name())
                ? Finding.pass("a " + Facts.lower(type) + " client", facts.categoryField())
                : Finding.fail("a " + Facts.lower(facts.category().get()) + " client, not " + Facts.lower(type),
                        facts.categoryField());
    }

    /** Art. 174 of the 2010 Law counts a portfolio under a discretionary management agreement as institutional. */
    private static Finding institutional(Facts facts) {
        if (facts.category().equals(Optional.of(Category.INSTITUTIONAL))) {
            return Finding.pass("an institutional client", facts.categoryField());
        }
        if (facts.mandateType().equals(Optional.of(MandateType.DISCRETIONARY))) {
            return Finding.pass("a discretionary mandate counts as institutional", facts.mandateTypeField());
        }
        if (facts.category().isEmpty() || facts.mandateType().isEmpty()) {
            return Finding.review("Investor Classification or mandate unknown", facts.categoryField(),
                    facts.mandateTypeField());
        }
        return Finding.fail("a " + Facts.lower(facts.category().get()) + " client under an "
                + Facts.lower(facts.mandateType().get()) + " mandate is not institutional",
                facts.categoryField(), facts.mandateTypeField());
    }

    /**
     * CISA art. 10(3): professional and institutional clients, HNWI opt-outs included. Art. 10(3ter): retail clients
     * under a mandate. A retail advisory client may be qualified under 10(3ter) only through a long-term advisory
     * relationship, which the Client Profile does not say: the dealer judges.
     */
    private static Finding qualified(Facts facts) {
        if (facts.category().isEmpty()) {
            return Finding.review("Investor Classification unknown", facts.categoryField());
        }
        if (facts.category().get() != Category.RETAIL) {
            return Finding.pass("a " + Facts.lower(facts.category().get())
                    + " client is a qualified investor (CISA art. 10(3))", facts.categoryField());
        }
        return switch (facts.mandateType().orElse(null)) {
            case DISCRETIONARY -> Finding.pass("a retail client under a discretionary mandate is a qualified investor"
                    + " (CISA art. 10(3ter))", facts.categoryField(), facts.mandateTypeField());
            case ADVISORY -> Finding.review("a retail advisory client is qualified (CISA art. 10(3ter)) only through"
                    + " a long-term advisory relationship", facts.categoryField(), facts.mandateTypeField());
            case EXECUTION_ONLY -> Finding.fail("a retail execution-only client is not a qualified investor",
                    facts.categoryField(), facts.mandateTypeField());
            case null -> Finding.review("mandate unknown", facts.categoryField(), facts.mandateTypeField());
        };
    }

    /** SIF Law art. 2(1): institutional, professional, or a written confirmation and at least EUR 100,000. */
    private Finding wellInformed(Facts facts) {
        if (facts.category().isPresent() && facts.category().get() != Category.RETAIL) {
            return Finding.pass("a " + Facts.lower(facts.category().get()) + " client is a well-informed investor",
                    facts.categoryField());
        }
        if (facts.declarations().isEmpty() || facts.category().isEmpty()) {
            return Finding.review("Investor Classification or declarations unknown", facts.categoryField(),
                    facts.declarationsField());
        }
        if (!facts.declared(DeclarationType.LU_WELL_INFORMED_CONFIRMATION)) {
            return Finding.fail("a retail client with no well-informed confirmation", facts.categoryField(),
                    facts.declarationsField());
        }
        Optional<SubscriptionRequest.Money> amount = facts.request().amount();
        if (amount.isEmpty()) {
            return Finding.review("a well-informed confirmation, but the request names no amount",
                    facts.declarationsField());
        }
        return money.in(amount.get(), "EUR")
                .map(eur -> eur.amount().compareTo(WELL_INFORMED_MINIMUM_EUR) >= 0
                        ? Finding.pass("a written well-informed confirmation and at least EUR 100,000",
                                facts.declarationsField()).citing(eur.rate().stream().toList())
                        : Finding.fail("a written well-informed confirmation, but below EUR 100,000",
                                facts.declarationsField()).citing(eur.rate().stream().toList()))
                .orElseGet(() -> Finding.review("no FX rate to EUR for " + amount.get().currency(),
                        facts.declarationsField()));
    }
}
