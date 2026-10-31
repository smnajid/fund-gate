package com.fundgate.ruleengine;

import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** The minimum initial subscription, compared in the currency the class states it in. */
public final class MinimumInitialSubscriptionKind
        implements RuleKindPlugin<MinimumInitialSubscriptionKind.MinimumInitialSubscription> {

    /** Below the minimum by at most this share gives Review rather than Fail (assumed, the desk confirms). */
    private static final BigDecimal MINIMUM_TOLERANCE = new BigDecimal("0.05");

    /** The minimum, in the currency the Fund Documentation states it in. */
    public record MinimumInitialSubscription(BigDecimal amount, String currency) implements Condition {

        public MinimumInitialSubscription {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
        }
    }

    @Override
    public RuleKind kind() {
        return RuleKind.MINIMUM_INITIAL_SUBSCRIPTION;
    }

    @Override
    public String name() {
        return "minimum_initial_subscription";
    }

    @Override
    public Class<MinimumInitialSubscription> condition() {
        return MinimumInitialSubscription.class;
    }

    @Override
    public String prompt() {
        return "- minimum_initial_subscription: amount and currency, as the document states them (0 if none).";
    }

    @Override
    public Optional<Finding> evaluate(MinimumInitialSubscription condition, Facts facts, Lookups lookups) {
        if (condition.amount().signum() <= 0) {
            return Optional.of(Finding.pass("no minimum initial subscription"));
        }
        String minimum = format(condition.amount(), condition.currency());
        SubscriptionRequest request = facts.request();
        if (request.amount().isEmpty()) {
            return Optional.of(Finding.review("the request names no amount; the minimum is " + minimum));
        }
        SubscriptionRequest.Money asked = request.amount().get();
        return Optional.of(lookups.money().in(asked, condition.currency())
                .map(converted -> {
                    String amount = format(asked.amount(), asked.currency())
                            + (converted.rate().isPresent() ? " is " + format(converted.amount(), converted.currency()) : "");
                    Finding finding;
                    if (converted.amount().compareTo(condition.amount()) >= 0) {
                        finding = Finding.pass(amount + ", at least the minimum of " + minimum);
                    } else {
                        BigDecimal tolerated = condition.amount().multiply(BigDecimal.ONE.subtract(MINIMUM_TOLERANCE));
                        finding = converted.amount().compareTo(tolerated) >= 0
                                ? Finding.review(amount + ", below the minimum of " + minimum + " by at most 5%")
                                : Finding.fail(amount + ", below the minimum of " + minimum);
                    }
                    return finding.citing(converted.rate().stream().toList());
                })
                .orElseGet(() -> Finding.review("no FX rate from " + asked.currency() + " to " + condition.currency())));
    }

    private static String format(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.ENGLISH);
        format.setRoundingMode(RoundingMode.DOWN);
        return currency + " " + format.format(amount);
    }
}
