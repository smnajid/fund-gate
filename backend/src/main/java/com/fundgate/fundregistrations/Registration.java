package com.fundgate.fundregistrations;

import com.fundgate.bankdata.InvestorType;
import java.util.Set;

/** The bank's record for one Share Class in one country. NotRegistered is a fact; Unknown means there is no list. */
public sealed interface Registration {

    record Registered(Set<InvestorType> investorTypes) implements Registration {

        public Registered {
            investorTypes = Set.copyOf(investorTypes);
        }
    }

    record NotRegistered() implements Registration {
    }

    record Unknown() implements Registration {
    }
}
