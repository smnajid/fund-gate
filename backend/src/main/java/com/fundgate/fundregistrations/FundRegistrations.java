package com.fundgate.fundregistrations;

import com.fundgate.bankdata.Country;
import com.fundgate.shareclasses.Isin;

/** Out port: the bank's Fund Registration (contracts/fund-registrations.md). */
public interface FundRegistrations {

    /** The bank's record for one Share Class in one country. Throws {@link com.fundgate.bankdata.FundDataUnavailable}. */
    Registration registration(Isin isin, Country country);
}
