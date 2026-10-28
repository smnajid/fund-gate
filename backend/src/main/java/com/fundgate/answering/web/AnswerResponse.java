package com.fundgate.answering.web;

import com.fundgate.answering.Answer;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.eligibility.Verdict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What the dealer screen shows for one email; the shape the Angular client is generated from. On a Privacy Gate
 * refusal, refusal is set, swaps and checks are empty and reply is null.
 */
public record AnswerResponse(
        Refusal refusal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Swap> swaps,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Check> checks,
        Reply reply) {

    static AnswerResponse of(Answer answer) {
        return switch (answer) {
            case Answer.Refused refused ->
                    new AnswerResponse(new Refusal(refused.reason(), refused.found()), List.of(), List.of(), null);
            case Answer.Answered answered -> new AnswerResponse(null,
                    answered.swaps().stream()
                            .map(swap -> new Swap(swap.portfolioRef().value(), swap.portfolioId().value()))
                            .toList(),
                    answered.checks().stream().map(Check::of).toList(),
                    new Reply(answered.reply().text(), answered.reply().draftedByAi()));
        };
    }

    /** No AI was used for this email: what the Privacy Gate found that stopped it. */
    public record Refusal(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> found) {
    }

    /** The reply to send; draftedByAi false means it is the template. */
    public record Reply(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean draftedByAi) {
    }

    /** The Privacy Gate line: a Portfolio Reference and the Portfolio Id the LLM saw instead. */
    public record Swap(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String portfolioRef,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID portfolioId) {
    }

    /** One Eligibility Check. Amount and currency are null when the request names no amount. */
    public record Check(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID checkId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String portfolioRef,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shareClass,
            BigDecimal amount,
            String currency,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ClientInitiated clientInitiated,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Verdict verdict,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> notices) {

        static Check of(Answer.Check check) {
            Money amount = check.request().amount().orElse(null);
            return new Check(check.checkId(), check.portfolioRef().value(), check.request().shareClass().value(),
                    amount == null ? null : amount.amount(), amount == null ? null : amount.currency(),
                    check.request().clientInitiated(), check.result().verdict(),
                    check.result().justification().notices());
        }
    }
}
