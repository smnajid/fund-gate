package com.fundgate.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.rulecard.Rule.Revision;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.shareclasses.Isin;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Rule Card Validator's steps on the frozen Draft of Nordea 1 GCE, after its Validated version 1 (#66). */
class RuleCardValidationTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    private static final Isin GCE_BP = new Isin("LU0348926287");
    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
    /** On the Draft: rule 5 reads the prospectus, rule 6 the KID, both on GCE-BI's investor category. */
    private static final int PROSPECTUS = 5;
    private static final int KID = 6;

    private RuleCardVersions versions;
    private RuleCardValidation validation;
    private RuleCard firstVersion;

    @BeforeEach
    void startFromTheFixtures() {
        RuleCardFiles files = new RuleCardFiles(RuleCatalog.conditionTypes());
        versions = new RuleCardVersions(files.read(FIXTURES.resolve("rule-cards")));
        firstVersion = versions.forShareClass(GCE_BP).orElseThrow();
        files.read(FIXTURES.resolve("draft-rule-cards")).forEach(versions::addDraft);
        validation = new RuleCardValidation(versions, RuleCatalog.conditionTypes(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void a_draft_is_numbered_after_the_validated_version_and_does_not_decide() {
        assertThat(versions.versionsOf(GCE_BP)).extracting(RuleCard::version).containsExactly(1, 2);
        assertThat(versions.forShareClass(GCE_BP)).contains(firstVersion);
    }

    @Test
    void following_the_prospectus_rejects_the_kid_s_reading() {
        RuleCard card = validation.follow(GCE_BP, 2, PROSPECTUS, "Cynthia");

        assertThat(card.rules().get(PROSPECTUS).history().getLast())
                .extracting(Revision::action, Revision::by, Revision::at)
                .containsExactly(Revision.Action.CONTRADICTION_RESOLVED, "Cynthia", NOW);
        assertThat(card.rules().get(KID).history().getLast().action()).isEqualTo(Revision.Action.REJECTED);
        assertThat(validation.review(GCE_BP, 2).contradictions()).allMatch(CardReview.Contradiction::settled);
    }

    @Test
    void accepting_a_contradicted_rule_does_not_settle_it() {
        reviewEveryRule();
        validation.accept(GCE_BP, 2, PROSPECTUS, "Cynthia");
        validation.accept(GCE_BP, 2, KID, "Cynthia");

        assertThat(validation.review(GCE_BP, 2).readyToValidate()).isFalse();
        assertThatThrownBy(() -> validation.validate(GCE_BP, 2, "Cynthia")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void once_validated_the_new_version_decides_and_neither_version_is_edited() {
        validation.follow(GCE_BP, 2, PROSPECTUS, "Cynthia");
        reviewEveryRule();

        RuleCard validated = validation.validate(GCE_BP, 2, "Cynthia");

        assertThat(validated.validation()).contains(new RuleCard.Validation("Cynthia", NOW));
        assertThat(versions.forShareClass(GCE_BP)).contains(validated);
        assertThat(versions.version(GCE_BP, 1)).contains(firstVersion);
        assertThatThrownBy(() -> validation.accept(GCE_BP, 2, 0, "Cynthia")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validation.accept(GCE_BP, 1, 0, "Cynthia")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void every_step_names_its_validator() {
        assertThatThrownBy(() -> validation.accept(GCE_BP, 2, 0, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    private void reviewEveryRule() {
        for (CardReview.RuleReview rule : validation.review(GCE_BP, 2).rules()) {
            if (rule.status() == CardReview.Status.OPEN && rule.index() != PROSPECTUS && rule.index() != KID) {
                validation.accept(GCE_BP, 2, rule.index(), "Cynthia");
            }
        }
    }
}
