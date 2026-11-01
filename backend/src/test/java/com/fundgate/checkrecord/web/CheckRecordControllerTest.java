package com.fundgate.checkrecord.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.AdaptersConfiguration;
import com.fundgate.checkrecord.AuditChecks;
import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecordConfiguration;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.checkrecord.DealerCorrection;
import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.Justification;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.eligibility.Verdict;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import com.fundgate.shareclasses.Isin;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** "What the AI saw": the dealer screen opens the Check Record of one check (screens.md, Dealer screen, step 5). */
// The platform reads its own frozen fixtures, never the demo content the team rewrites (#80)
@WebMvcTest(value = CheckRecordController.class, properties = "fundeli5.fixtures.dir=src/test/resources/fixtures")
@Import({AdaptersConfiguration.class, CheckRecordConfiguration.class})
class CheckRecordControllerTest {

    private static final Instant CHECKED_AT = Instant.parse("2026-11-04T09:30:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    CheckRecords checkRecords;

    @Autowired
    AuditChecks auditChecks;

    @Test
    void a_check_record_gives_back_what_the_check_read_the_llm_calls_and_the_corrections_beside_it() {
        CheckRecord record = record();
        checkRecords.write(record);
        auditChecks.correct(record.checkId(), DealerCorrection.Field.CURRENCY, "CHF", "dealer-1");

        var response = get(record.checkId());

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.checkId").isEqualTo(record.checkId().toString());
        assertThat(response).bodyJson().extractingPath("$.portfolioRef").isEqualTo("LOC90000101");
        assertThat(response).bodyJson().extractingPath("$.request.currency").isEqualTo("EUR");
        assertThat(response).bodyJson().extractingPath("$.ruleCard.fundName").isEqualTo("Nordea 1 Global Climate");
        assertThat(response).bodyJson().extractingPath("$.ruleCard.version").isEqualTo(2);
        assertThat(response).bodyJson().extractingPath("$.scaleMappingVersion").isEqualTo("pending");
        assertThat(response).bodyJson().extractingPath("$.engineVersion").isEqualTo("0.1.0 built today");
        assertThat(response).bodyJson().extractingPath("$.clientProfileFields[*].field")
                .asArray().containsExactly("classification.category");
        assertThat(response).bodyJson().extractingPath("$.verdict").isEqualTo("NOT_ELIGIBLE");
        assertThat(response).bodyJson().extractingPath("$.llmCalls[0].prompt").isEqualTo("Parse 9ae4…");
        assertThat(response).bodyJson().extractingPath("$.llmCalls[0].fieldsSent")
                .asArray().containsExactly("risk profile");
        assertThat(response).bodyJson().extractingPath("$.llmCalls[0].responseModel").isEqualTo("claude");
        assertThat(response).bodyJson().extractingPath("$.corrections[0].parsed").isEqualTo("EUR");
        assertThat(response).bodyJson().extractingPath("$.corrections[0].corrected").isEqualTo("CHF");
    }

    @Test
    void a_check_id_with_no_record_is_not_found() {
        assertThat(get(UUID.randomUUID())).hasStatus(404);
    }

    private MvcTestResult get(UUID checkId) {
        return mvc.get().uri("/api/check-records/{checkId}", checkId).exchange();
    }

    private static CheckRecord record() {
        SubscriptionRequest request = new SubscriptionRequest(new PortfolioId(UUID.randomUUID()),
                new Isin("LU0348927095"), Optional.of(new Money(new BigDecimal("80000"), "EUR")), ClientInitiated.NO);
        CheckRecord.LlmCall parsing = new CheckRecord.LlmCall("parse the request",
                new LlmRequest("claude", "You read requests", "Parse 9ae4…"), List.of("risk profile"),
                new LlmResponse("claude", "{}"), CHECKED_AT);
        return new CheckRecord(UUID.randomUUID(), CHECKED_AT, "not signed in", new PortfolioReference("LOC90000101"),
                request, Optional.of(new CheckRecord.RuleCardVersion(Optional.empty(), "Nordea 1 Global Climate", 2)),
                "pending", "0.1.0 built today", List.of(new Citation.ClientProfileField("classification.category",
                "retail")), Verdict.NOT_ELIGIBLE, new Justification(List.of(), List.of()), List.of(parsing));
    }
}
