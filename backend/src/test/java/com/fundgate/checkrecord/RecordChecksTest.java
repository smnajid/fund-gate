package com.fundgate.checkrecord;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.checkrecord.memory.InMemoryCheckRecords;
import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.Justification;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.Verdict;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.rulecard.RuleKind;
import com.fundgate.shareclasses.Isin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 07-audit-a-check.feature: every Eligibility Check writes one Check Record with everything needed to reproduce it. */
class RecordChecksTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:30:00Z");

    private static final Isin GCE_BI = new Isin("LU0348927095");

    private static final RuleCard NORDEA_V2 = new RuleCard(Optional.of("nordea1-gce"), "Nordea 1 Global Climate",
            List.of(GCE_BI), 2, RuleCard.Status.VALIDATED, List.of(), List.of(),
            new RuleCard.Drafting("model", "endpoint", NOW), Optional.empty());

    private final CheckRecords records = new InMemoryCheckRecords();

    private final RecordChecks recordChecks = new RecordChecks(records,
            shareClass -> shareClass.equals(GCE_BI) ? Optional.of(NORDEA_V2) : Optional.empty(), "0.1.0 built today",
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void the_record_holds_the_request_the_versions_the_check_read_and_its_verdict() {
        SubscriptionRequest request = request(GCE_BI);
        EligibilityResult result = result(new Citation.ClientProfileField("classification.category", "retail"));

        UUID checkId = recordChecks.record(new PortfolioReference("LOC90000101"), request, result, List.of());

        CheckRecord record = records.find(checkId).orElseThrow();
        assertThat(record.checkedAt()).isEqualTo(NOW);
        assertThat(record.portfolioRef()).isEqualTo(new PortfolioReference("LOC90000101"));
        assertThat(record.request()).isEqualTo(request);
        assertThat(record.ruleCard()).contains(
                new CheckRecord.RuleCardVersion(Optional.of("nordea1-gce"), "Nordea 1 Global Climate", 2));
        assertThat(record.scaleMappingVersion()).isEqualTo(RecordChecks.SCALE_MAPPING_PENDING);
        assertThat(record.engineVersion()).isEqualTo("0.1.0 built today");
        assertThat(record.verdict()).isEqualTo(result.verdict());
        assertThat(record.justification()).isEqualTo(result.justification());
    }

    @Test
    void the_client_profile_snapshot_is_every_field_the_justification_cites_once() {
        Citation.ClientProfileField category = new Citation.ClientProfileField("classification.category", "retail");
        Citation.ClientProfileField residence = new Citation.ClientProfileField("client.residenceCountry", "CH");
        EligibilityResult result = result(category, residence, category,
                new Citation.Document(new DocumentId("prospectus"), 5, "institutional investors", Optional.empty()));

        UUID checkId = recordChecks.record(new PortfolioReference("LOC90000101"), request(GCE_BI), result, List.of());

        assertThat(records.find(checkId).orElseThrow().clientProfileSnapshot()).containsExactly(category, residence);
    }

    @Test
    void a_class_with_no_rule_card_is_recorded_with_none() {
        UUID checkId = recordChecks.record(new PortfolioReference("LOC90000101"), request(new Isin("CH0112537516")),
                new EligibilityResult(Verdict.NEEDS_REVIEW, new Justification(List.of(), List.of("Fund not covered"))),
                List.of());

        assertThat(records.find(checkId).orElseThrow().ruleCard()).isEmpty();
    }

    @Test
    void every_record_is_a_new_one_and_keeps_the_llm_calls_it_was_given() {
        CheckRecord.LlmCall call = new CheckRecord.LlmCall("parse the request", new LlmRequest("model", "system",
                "prompt"), List.of(), new LlmResponse("model", "answer"), NOW);
        EligibilityResult result = result();

        UUID first = recordChecks.record(new PortfolioReference("LOC90000101"), request(GCE_BI), result, List.of(call));
        UUID second = recordChecks.record(new PortfolioReference("LOC90000101"), request(GCE_BI), result, List.of());

        assertThat(second).isNotEqualTo(first);
        assertThat(records.find(first).orElseThrow().llmCalls()).containsExactly(call);
        assertThat(records.find(second).orElseThrow().llmCalls()).isEmpty();
    }

    private static SubscriptionRequest request(Isin shareClass) {
        return new SubscriptionRequest(new PortfolioId(UUID.fromString("9ae403db-1754-4033-9f06-a99edf92a574")),
                shareClass, Optional.empty(), ClientInitiated.UNKNOWN);
    }

    private static EligibilityResult result(Citation... citations) {
        return new EligibilityResult(Verdict.NOT_ELIGIBLE, new Justification(List.of(new RuleOutcome(
                RuleKind.INVESTOR_CATEGORY, RuleOutcome.Outcome.FAIL, "not institutional", List.of(citations))),
                List.of()));
    }
}
