package com.fundgate.fundregistrations;

import com.fundgate.bankdata.Country;
import com.fundgate.bankdata.InvestorType;
import com.fundgate.shareclasses.Isin;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Every country one Share Class is registered in, as the contract writes it. Absent registrations: Unknown. */
public record FundRegistration(Isin isin, Optional<List<Entry>> registrations, Instant retrievedAt) {

    public FundRegistration {
        Objects.requireNonNull(isin, "isin");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
    }

    /** {@code investorTypes} is required: the adapter must not invent ALL for an entry that names none. */
    public record Entry(Country country, Set<InvestorType> investorTypes) {

        public Entry {
            Objects.requireNonNull(country, "country");
            investorTypes = Set.copyOf(Objects.requireNonNull(investorTypes, "investorTypes"));
        }
    }

    public Registration in(Country country) {
        return registrations
                .map(entries -> entries.stream()
                        .filter(entry -> entry.country().equals(country))
                        .findFirst()
                        .<Registration>map(entry -> new Registration.Registered(entry.investorTypes()))
                        .orElseGet(Registration.NotRegistered::new))
                .orElseGet(Registration.Unknown::new);
    }
}
