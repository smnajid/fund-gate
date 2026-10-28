package com.fundgate.answering;

import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.shareclasses.Isin;
import java.util.Objects;
import java.util.Optional;

/** A Subscription Request the Fund Dealer entered in the form, after a Privacy Gate refusal (screens.md). */
public record RequestByHand(PortfolioReference portfolioRef, Isin shareClass, Optional<Money> amount,
        ClientInitiated clientInitiated) {

    public RequestByHand {
        Objects.requireNonNull(portfolioRef, "Portfolio Reference");
        Objects.requireNonNull(shareClass, "Share Class");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(clientInitiated, "client initiated");
    }
}
