package com.fundgate.fundregistrations;

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

    /** Who the class may be offered to in a country, a legal category (ADR 0008). ALL covers everyone. */
    enum InvestorType { ALL, RETAIL, PROFESSIONAL, INSTITUTIONAL, QUALIFIED, WELL_INFORMED }
}
