package com.fundgate.ruleengine;

import com.fundgate.bankdata.Country;
import com.fundgate.bankdata.InvestorType;
import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.fundregistrations.FundRegistration;
import com.fundgate.fundregistrations.FundRegistrations;
import com.fundgate.fundregistrations.Registration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Share Class must be registered for the client's country of residence, as the bank's Fund Registration says,
 * not the Fund Documentation's Selling Restriction (issue #41). Reverse solicitation turns a Fail into Review.
 */
class FundRegistrationRule {

    private final FundRegistrations fundRegistrations;
    private final InvestorTypes investorTypes;

    FundRegistrationRule(FundRegistrations fundRegistrations, InvestorTypes investorTypes) {
        this.fundRegistrations = fundRegistrations;
        this.investorTypes = investorTypes;
    }

    Finding evaluate(Facts facts) {
        Optional<Country> country = facts.profile().client().flatMap(ClientProfile.Client::residenceCountry);
        Citation residence = Facts.field("client.residenceCountry", country);
        if (country.isEmpty()) {
            return Finding.review("the client's country of residence is unknown", residence);
        }
        Optional<FundRegistration> fundRegistration = fundRegistrations.fundRegistration(facts.request().shareClass());
        if (fundRegistration.isEmpty()) {
            return Finding.review("the bank has no Fund Registration for the class", residence);
        }
        Registration registration = fundRegistration.get().in(country.get());
        Citation entry = new Citation.FundRegistration(facts.request().shareClass(), country.get(),
                entry(registration), fundRegistration.get().retrievedAt());
        return switch (registration) {
            case Registration.Registered registered -> registered.investorTypes().contains(InvestorType.ALL)
                    ? Finding.pass("registered in " + country.get() + " for all investors", residence, entry)
                    : forTheClient(investorTypes.isAnyOf(registered.investorTypes(), facts), country.get())
                            .citing(List.of(residence, entry));
            case Registration.NotRegistered notRegistered -> switch (facts.request().clientInitiated()) {
                case YES -> Finding.review("not registered in " + country.get()
                        + ", but the client initiated the order: record the evidence of reverse solicitation",
                        residence, entry);
                case NO -> Finding.fail("not registered in " + country.get(), residence, entry);
                case UNKNOWN -> Finding.review("not registered in " + country.get()
                        + ", and whether the client initiated the order is unknown", residence, entry);
            };
            case Registration.Unknown unknown -> Finding.review("the bank's Fund Registration lists no countries",
                    residence, entry);
        };
    }

    private static Finding forTheClient(Finding investorType, Country country) {
        String reason = "registered in " + country + " for " + investorType.reason();
        return new Finding(investorType.outcome(), reason, investorType.citations());
    }

    private static String entry(Registration registration) {
        return switch (registration) {
            case Registration.Registered registered -> "registered for " + registered.investorTypes().stream()
                    .sorted().map(Facts::lower).collect(Collectors.joining(", "));
            case Registration.NotRegistered notRegistered -> "not registered";
            case Registration.Unknown unknown -> "no registration list";
        };
    }
}
