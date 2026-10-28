package com.fundgate.ruleengine;

import com.fundgate.bankdata.Country;
import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.clientprofiles.ClientProfile.Classification.Category;
import com.fundgate.clientprofiles.ClientProfile.Classification.DeclarationType;
import com.fundgate.clientprofiles.ClientProfile.Mandate;
import com.fundgate.clientprofiles.ClientProfile.MandateType;
import com.fundgate.clientprofiles.ClientProfile.Manager;
import com.fundgate.clientprofiles.ClientProfile.ManagerKind;
import com.fundgate.eligibility.RuleOutcome.Outcome;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.Condition.InvestorCategory;
import com.fundgate.rulecard.Condition.UsPersonExclusion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** One evaluator per kind of rule on a Rule Card: fixed code, the card only fills in the parameters. */
class HardRestrictions {

    private static final Country US = new Country("US");

    /** Below the minimum by at most this share gives Review rather than Fail (assumed, Samuel confirms). */
    private static final BigDecimal MINIMUM_TOLERANCE = new BigDecimal("0.05");

    private final InvestorTypes investorTypes;
    private final Money money;

    HardRestrictions(InvestorTypes investorTypes, Money money) {
        this.investorTypes = investorTypes;
        this.money = money;
    }

    /** Empty for a Target Market dimension, which the Scale Mapping matches (#59). */
    Optional<Finding> evaluate(Condition condition, Facts facts) {
        return switch (condition) {
            case InvestorCategory investorCategory -> Optional.of(investorCategory(investorCategory, facts));
            case UsPersonExclusion usPerson -> Optional.of(usPerson(usPerson, facts.profile()));
            case Condition.MinimumInitialSubscription minimum -> Optional.of(minimum(minimum, facts.request()));
            case Condition.MandateOnlyClass mandateOnly -> Optional.of(mandateOnly(mandateOnly, facts));
            case Condition.KidRequiredForRetail kid -> Optional.of(kid(kid, facts));
            case Condition.UnmodelledRestriction unmodelled -> Optional.of(unmodelled(unmodelled));
            case Condition.TargetMarket targetMarket -> Optional.empty();
        };
    }

    private Finding investorCategory(InvestorCategory condition, Facts facts) {
        Finding allowed = investorTypes.isAnyOf(condition.allowed(), facts);
        if (allowed.outcome() != Outcome.PASS || condition.excluded().isEmpty()) {
            return allowed;
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
        return excludedOrNot.outcome() == Outcome.PASS ? allowed : excludedOrNot;
    }

    /**
     * Each definition names the facts that make a US Person; a known one gives Fail, an unknown one Review. Reg S
     * is residence, a Green Card included; some funds add citizenship; the IRC adds FATCA status.
     */
    private static Finding usPerson(UsPersonExclusion condition, ClientProfile profile) {
        if (condition.definition() == UsPersonExclusion.UsPersonDefinition.MIXED) {
            return Finding.review("the documents mix US Person definitions");
        }
        Optional<ClientProfile.Client> client = profile.client();
        Optional<Country> residence = client.flatMap(ClientProfile.Client::residenceCountry);
        Optional<Boolean> greenCard = client.flatMap(ClientProfile.Client::usPerson)
                .flatMap(ClientProfile.UsPerson::usPermanentResident);
        Optional<List<Country>> nationalities = client.flatMap(ClientProfile.Client::nationalities);
        Optional<ClientProfile.FatcaStatus> fatca = client.flatMap(ClientProfile.Client::usPerson)
                .flatMap(ClientProfile.UsPerson::fatcaStatus);

        List<Finding> facts = new ArrayList<>();
        if (condition.definition() != UsPersonExclusion.UsPersonDefinition.FATCA) {
            facts.add(fact(residence.map(US::equals), "resident in the United States",
                    Facts.field("client.residenceCountry", residence)));
            facts.add(fact(greenCard, "a US permanent resident",
                    Facts.field("client.usPerson.usPermanentResident", greenCard)));
        }
        if (condition.excludesUsCitizens() || condition.definition() == UsPersonExclusion.UsPersonDefinition.IRC_CITIZENSHIP) {
            facts.add(fact(nationalities.map(all -> all.contains(US)), "a US citizen",
                    Facts.field("client.nationalities", nationalities)));
        }
        if (condition.definition() == UsPersonExclusion.UsPersonDefinition.IRC_CITIZENSHIP
                || condition.definition() == UsPersonExclusion.UsPersonDefinition.FATCA) {
            facts.add(fact(fatca.map(ClientProfile.FatcaStatus.US_PERSON::equals), "a US Person for FATCA",
                    Facts.field("client.usPerson.fatcaStatus", fatca)));
        }
        Finding worst = Finding.worst(facts);
        return switch (worst.outcome()) {
            case PASS -> Finding.pass("not a US Person under the fund's definition")
                    .citing(facts.stream().flatMap(fact -> fact.citations().stream()).toList());
            case REVIEW -> Finding.review("US Person status unknown: " + worst.reason()).citing(worst.citations());
            default -> worst;
        };
    }

    /** True makes a US Person, false does not, unknown needs review. */
    private static Finding fact(Optional<Boolean> isUsPerson, String what, Citation field) {
        return isUsPerson.map(yes -> yes ? Finding.fail(what, field) : Finding.pass("not " + what, field))
                .orElseGet(() -> Finding.review("whether " + what, field));
    }

    private Finding minimum(Condition.MinimumInitialSubscription condition, SubscriptionRequest request) {
        if (condition.amount().signum() <= 0) {
            return Finding.pass("no minimum initial subscription");
        }
        String minimum = format(condition.amount(), condition.currency());
        if (request.amount().isEmpty()) {
            return Finding.review("the request names no amount; the minimum is " + minimum);
        }
        SubscriptionRequest.Money asked = request.amount().get();
        return money.in(asked, condition.currency())
                .map(converted -> {
                    String amount = format(asked.amount(), asked.currency())
                            + (converted.rate().isPresent() ? " is " + format(converted.amount(), converted.currency()) : "");
                    Finding finding;
                    if (converted.amount().compareTo(condition.amount()) >= 0) {
                        finding = Finding.pass(amount + ", at least the minimum of " + minimum);
                    } else {
                        BigDecimal tolerated = condition.amount().multiply(BigDecimal.ONE.subtract(MINIMUM_TOLERANCE));
                        finding = converted.amount().compareTo(tolerated) >= 0
                                ? Finding.review(amount + ", below the minimum of " + minimum + " by at most 5%")
                                : Finding.fail(amount + ", below the minimum of " + minimum);
                    }
                    return finding.citing(converted.rate().stream().toList());
                })
                .orElseGet(() -> Finding.review("no FX rate from " + asked.currency() + " to " + condition.currency()));
    }

    /** The class needs a mandate with the manager it names; the bank's own advice or mandate is not one. */
    private static Finding mandateOnly(Condition.MandateOnlyClass condition, Facts facts) {
        Optional<Mandate> mandate = facts.mandate();
        Optional<Manager> manager = mandate.flatMap(Mandate::manager);
        Optional<String> managerName = manager.flatMap(Manager::name);
        Citation[] fields = {facts.mandateTypeField(),
                Facts.field("portfolio.mandate.manager.kind", manager.flatMap(Manager::kind)),
                Facts.field("portfolio.mandate.manager.name", managerName)};
        if (condition.manager().isEmpty()) {
            return facts.mandateType().map(type -> type == MandateType.DISCRETIONARY
                            ? Finding.pass("a discretionary mandate", fields)
                            : Finding.fail("no discretionary mandate", fields))
                    .orElseGet(() -> Finding.review("mandate unknown", fields));
        }
        String needed = condition.manager().get();
        if (managerName.isPresent()) {
            return managerName.get().toLowerCase(Locale.ROOT).contains(needed.toLowerCase(Locale.ROOT))
                    ? Finding.pass("a mandate with " + needed, fields)
                    : Finding.fail("a mandate with " + managerName.get() + ", not " + needed, fields);
        }
        boolean withTheBank = manager.flatMap(Manager::kind).equals(Optional.of(ManagerKind.BANK))
                || facts.mandateType().filter(type -> type != MandateType.DISCRETIONARY).isPresent();
        if (withTheBank) {
            return Finding.fail("no mandate with " + needed + ": the portfolio is advised or managed by the bank", fields);
        }
        return Finding.review("whether the portfolio has a mandate with " + needed, fields);
    }

    /** A retail client needs a KID (PRIIPs, FinSA); without one the class is closed to retail clients. */
    private static Finding kid(Condition.KidRequiredForRetail condition, Facts facts) {
        if (condition.kidAvailable()) {
            return Finding.pass("a KID is available");
        }
        return facts.category()
                .map(category -> category == Category.RETAIL
                        ? Finding.fail("no KID for a retail client", facts.categoryField())
                        : Finding.pass("no KID needed for a " + Facts.lower(category) + " client", facts.categoryField()))
                .orElseGet(() -> Finding.review("no KID, and the Investor Classification is unknown",
                        facts.categoryField()));
    }

    /** ADR 0003: Blocking gives Review; Noted passes and stays in the Justification with its Citation. */
    private static Finding unmodelled(Condition.UnmodelledRestriction condition) {
        return switch (condition.status()) {
            case BLOCKING -> Finding.review("Blocking: a restriction the engine cannot check");
            case NOTED -> Finding.pass("Noted" + condition.notedReason().map(reason -> ": " + reason).orElse(""));
        };
    }

    static String format(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.ENGLISH);
        format.setRoundingMode(RoundingMode.DOWN);
        return currency + " " + format.format(amount);
    }
}
