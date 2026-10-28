package com.fundgate.ruleengine;

import com.fundgate.citation.Citation;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.fxrates.FxRate;
import com.fundgate.fxrates.FxRates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** Converts an amount into another currency at the bank's FX table, citing the rate it used. */
class Money {

    private final FxRates fxRates;

    Money(FxRates fxRates) {
        this.fxRates = fxRates;
    }

    /** An amount in a currency, and the rate it was converted at, if any. */
    record Converted(BigDecimal amount, String currency, Optional<Citation.FxRate> rate) {
    }

    /** Empty when the table has no rate between the two currencies. */
    Optional<Converted> in(SubscriptionRequest.Money money, String currency) {
        if (money.currency().equals(currency)) {
            return Optional.of(new Converted(money.amount(), currency, Optional.empty()));
        }
        Optional<Converted> direct = fxRates.rate(money.currency(), currency)
                .map(rate -> converted(money.amount().multiply(rate.rate()), currency, rate));
        return direct.or(() -> fxRates.rate(currency, money.currency())
                .map(rate -> converted(money.amount().divide(rate.rate(), 2, RoundingMode.HALF_EVEN), currency, rate)));
    }

    private static Converted converted(BigDecimal amount, String currency, FxRate rate) {
        return new Converted(amount.setScale(2, RoundingMode.HALF_EVEN), currency,
                Optional.of(new Citation.FxRate(rate.pair(), rate.rate(), rate.date(), rate.source())));
    }
}
