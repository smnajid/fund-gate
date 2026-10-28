package com.fundgate.eligibility;

import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.shareclasses.Isin;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** One portfolio asking to subscribe to one Share Class: the unit of an Eligibility Check (issue #6). */
public record SubscriptionRequest(
        PortfolioId portfolioId,
        Isin shareClass,
        /** Empty when the request names no amount (coverage row 26). */
        Optional<Money> amount,
        ClientInitiated clientInitiated) {

    public SubscriptionRequest {
        Objects.requireNonNull(portfolioId, "Portfolio Id");
        Objects.requireNonNull(shareClass, "Share Class");
        Objects.requireNonNull(clientInitiated, "client initiated");
    }

    public record Money(BigDecimal amount, String currency) {
    }

    /** Whether the client initiated the order: reverse solicitation turns a registration Fail into Review. */
    public enum ClientInitiated { YES, NO, UNKNOWN }
}
