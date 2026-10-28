package com.fundgate.clientprofiles;

import java.util.Optional;

/** Out port: the Client Profile of one portfolio (contracts/client-profile.md). */
public interface ClientProfiles {

    /** For the Privacy Gate. Empty when the bank does not know the reference. */
    Optional<PortfolioId> portfolioId(PortfolioReference ref);

    /** For the Rule Engine. Throws {@link PortfolioNotFound} or {@link ClientDataUnavailable}. */
    ClientProfile clientProfile(PortfolioReference ref);
}
