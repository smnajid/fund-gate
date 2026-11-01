package com.fundgate.checkrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.checkrecord.memory.InMemoryCheckRecords;
import com.fundgate.checkrecord.memory.InMemoryDealerCorrections;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.Justification;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.eligibility.Verdict;
import com.fundgate.shareclasses.Isin;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 07-audit-a-check.feature: a Check Record is never changed; a correction is recorded beside it. */
class AuditChecksTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

    private final CheckRecords records = new InMemoryCheckRecords();

    private final AuditChecks audit = new AuditChecks(records, new InMemoryDealerCorrections(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void a_correction_is_recorded_beside_the_record_which_keeps_what_the_ai_read() {
        CheckRecord record = record(Optional.of(new Money(new BigDecimal("80000"), "EUR")));
        records.write(record);

        audit.correct(record.checkId(), DealerCorrection.Field.CURRENCY, "CHF", "dealer-1");

        AuditedCheck audited = audit.audit(record.checkId()).orElseThrow();
        assertThat(audited.record()).isEqualTo(record);
        assertThat(audited.corrections()).singleElement().isEqualTo(new DealerCorrection(record.checkId(),
                DealerCorrection.Field.CURRENCY, Optional.of("EUR"), "CHF", "dealer-1", NOW));
    }

    @Test
    void a_correction_of_what_the_ai_did_not_read_keeps_that_it_read_nothing() {
        CheckRecord record = record(Optional.empty());
        records.write(record);

        assertThat(audit.correct(record.checkId(), DealerCorrection.Field.AMOUNT, "80000", "dealer-1").parsed())
                .isEmpty();
    }

    @Test
    void a_correction_needs_a_check_record() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> audit.correct(unknown, DealerCorrection.Field.CURRENCY, "CHF", "dealer-1"))
                .isInstanceOf(CheckRecordNotFound.class);
        assertThat(audit.audit(unknown)).isEmpty();
    }

    @Test
    void a_check_record_is_written_once() {
        CheckRecord record = record(Optional.empty());
        records.write(record);

        assertThatThrownBy(() -> records.write(record(record.checkId(), Optional.empty())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(records.find(record.checkId())).contains(record);
    }

    private static CheckRecord record(Optional<Money> amount) {
        return record(UUID.randomUUID(), amount);
    }

    private static CheckRecord record(UUID checkId, Optional<Money> amount) {
        SubscriptionRequest request = new SubscriptionRequest(new PortfolioId(UUID.randomUUID()),
                new Isin("LU0348927095"), amount, ClientInitiated.NO);
        return new CheckRecord(checkId, NOW, "dealer-1", new PortfolioReference("LOC90000101"), request,
                Optional.empty(), "scale-mapping-1", "engine-1", List.of(), Verdict.NEEDS_REVIEW,
                new Justification(List.of(), List.of()), List.of());
    }
}
