package com.fundgate.fundregistrations;

import com.fundgate.bankdata.Country;
import com.fundgate.shareclasses.Isin;
import java.util.Optional;

/** Out port: the bank's Fund Registration (contracts/fund-registrations.md). */
public interface FundRegistrations {

    /**
     * The bank's record for one Share Class, with when it was read; empty when the bank has none.
     * Throws {@link com.fundgate.bankdata.FundDataUnavailable}.
     */
    Optional<FundRegistration> fundRegistration(Isin isin);

    /** The bank's record for one Share Class in one country. Throws {@link com.fundgate.bankdata.FundDataUnavailable}. */
    default Registration registration(Isin isin, Country country) {
        return fundRegistration(isin)
                .map(fundRegistration -> fundRegistration.in(country))
                .orElseGet(Registration.Unknown::new);
    }
}
