package com.fundgate.checkrecord;

import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * In port: reads a Check Record back with the corrections beside it, and records a correction without touching the
 * record, which keeps what the AI read (architecture.md, Audit).
 */
public class AuditChecks {

    private final CheckRecords checkRecords;
    private final DealerCorrections corrections;
    private final Clock clock;

    public AuditChecks(CheckRecords checkRecords, DealerCorrections corrections, Clock clock) {
        this.checkRecords = checkRecords;
        this.corrections = corrections;
        this.clock = clock;
    }

    public Optional<AuditedCheck> audit(UUID checkId) {
        return checkRecords.find(checkId).map(record -> new AuditedCheck(record, corrections.of(checkId)));
    }

    /** Throws {@link CheckRecordNotFound}. */
    public DealerCorrection correct(UUID checkId, DealerCorrection.Field field, String corrected, String dealer) {
        CheckRecord record = checkRecords.find(checkId).orElseThrow(() -> new CheckRecordNotFound(checkId));
        DealerCorrection correction = new DealerCorrection(checkId, field, parsed(record.request(), field), corrected,
                dealer, clock.instant());
        corrections.append(correction);
        return correction;
    }

    private static Optional<String> parsed(SubscriptionRequest request, DealerCorrection.Field field) {
        return switch (field) {
            case SHARE_CLASS -> Optional.of(request.shareClass().value());
            case AMOUNT -> request.amount().map(money -> money.amount().toPlainString());
            case CURRENCY -> request.amount().map(Money::currency);
            case CLIENT_INITIATED -> Optional.of(request.clientInitiated().name());
        };
    }
}
