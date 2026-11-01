package com.fundgate.checkrecord.web;

import com.fundgate.checkrecord.AuditedCheck;
import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.DealerCorrection;
import com.fundgate.citation.Citation;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.eligibility.Verdict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the side drawer shows for one check; the shape the Angular client is generated from. The Justification is
 * left out: the screen already shows it, and the record holds it unchanged. ruleCard is null when the class has none.
 */
public record CheckRecordResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID checkId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant checkedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String dealer,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String portfolioRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RecordedRequest request,
        RuleCardVersion ruleCard,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String scaleMappingVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String engineVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ClientProfileField> clientProfileFields,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Verdict verdict,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LlmCall> llmCalls,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Correction> corrections) {

    static CheckRecordResponse of(AuditedCheck audited) {
        CheckRecord record = audited.record();
        return new CheckRecordResponse(record.checkId(), record.checkedAt(), record.dealer(),
                record.portfolioRef().value(), RecordedRequest.of(record.request()),
                record.ruleCard().map(RuleCardVersion::of).orElse(null), record.scaleMappingVersion(),
                record.engineVersion(), record.clientProfileSnapshot().stream().map(ClientProfileField::of).toList(),
                record.verdict(), record.llmCalls().stream().map(LlmCall::of).toList(),
                audited.corrections().stream().map(Correction::of).toList());
    }

    /** The Subscription Request as the AI read it. Amount and currency are null when it names no amount. */
    public record RecordedRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID portfolioId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shareClass,
            BigDecimal amount,
            String currency,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ClientInitiated clientInitiated) {

        static RecordedRequest of(SubscriptionRequest request) {
            Money amount = request.amount().orElse(null);
            return new RecordedRequest(request.portfolioId().value(), request.shareClass().value(),
                    amount == null ? null : amount.amount(), amount == null ? null : amount.currency(),
                    request.clientInitiated());
        }
    }

    /** The version of the Rule Card the check read. fundId is null when FMD has none. */
    public record RuleCardVersion(
            String fundId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fundName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version) {

        static RuleCardVersion of(CheckRecord.RuleCardVersion card) {
            return new RuleCardVersion(card.fundId().orElse(null), card.fundName(), card.version());
        }
    }

    /** A Client Profile field the check read, with its value at check time. */
    public record ClientProfileField(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value) {

        static ClientProfileField of(Citation.ClientProfileField field) {
            return new ClientProfileField(field.field(), field.value());
        }
    }

    /** One LLM call, exactly as it went out after the Privacy Gate, and what came back. */
    public record LlmCall(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String purpose,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String model,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String system,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String prompt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> fieldsSent,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String responseModel,
            String response,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant calledAt) {

        static LlmCall of(CheckRecord.LlmCall call) {
            return new LlmCall(call.purpose(), call.gatedRequest().model(), call.gatedRequest().system(),
                    call.gatedRequest().prompt(), call.fieldsSent(), call.response().model(), call.response().text(),
                    call.calledAt());
        }
    }

    /** A field the Fund Dealer corrected beside the record. parsed is null when the AI read nothing. */
    public record Correction(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DealerCorrection.Field field,
            String parsed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String corrected,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String dealer,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant correctedAt) {

        static Correction of(DealerCorrection correction) {
            return new Correction(correction.field(), correction.parsed().orElse(null), correction.corrected(),
                    correction.dealer(), correction.correctedAt());
        }
    }
}
