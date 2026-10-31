package com.fundgate;

import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.fixture.FixtureClientProfiles;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.funddocuments.folder.FolderFundDocuments;
import com.fundgate.fundregistrations.FundRegistrations;
import com.fundgate.fundregistrations.fixture.FixtureFundRegistrations;
import com.fundgate.fxrates.FxRates;
import com.fundgate.fxrates.fixture.FixtureFxRates;
import com.fundgate.llm.LlmEgress;
import com.fundgate.llm.replay.ReplayLlmEgress;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.rulecard.fixture.FixtureRuleCards;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.shareclasses.ShareClasses;
import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The adapter behind each out port, chosen per port (ADR 0008). The dealer app reads client data; the pipeline
 * reads only public Fund Documentation, so it has no Client Profile adapter at all (ADR 0004).
 * Paths are Strings: Spring's String-to-Path conversion goes through the web resource loader, which refuses "../".
 */
@Configuration
public class AdaptersConfiguration {

    @Configuration
    @Profile("!pipeline")
    static class DealerApp {

        @Bean
        ClientProfiles clientProfiles(@Value("${fundeli5.fixtures.dir}") String fixtures) {
            return new FixtureClientProfiles(Path.of(fixtures, "client-profiles"));
        }

        @Bean
        FundRegistrations fundRegistrations(@Value("${fundeli5.fixtures.dir}") String fixtures) {
            return new FixtureFundRegistrations(Path.of(fixtures, "fund-registrations.json"));
        }

        @Bean
        FxRates fxRates(@Value("${fundeli5.fixtures.dir}") String fixtures) {
            return new FixtureFxRates(Path.of(fixtures, "fx-rates.json"));
        }

        @Bean
        RuleCards ruleCards(@Value("${fundeli5.fixtures.dir}") String fixtures) {
            return new FixtureRuleCards(Path.of(fixtures, "rule-cards"), RuleCatalog.conditionTypes());
        }
    }

    /**
     * Public data, read by both runtimes: the pipeline drafts from the documents, the dealer app reads only the
     * editions FMD lists, to warn of a Stale Rule Card.
     */
    @Bean
    FundDocuments fundDocuments(@Value("${fundeli5.fixtures.dir}") String fixtures,
            @Value("${fundeli5.documents.folder}") String folder, ShareClasses shareClasses) {
        return new FolderFundDocuments(Path.of(fixtures, "fund-documents.json"), Path.of(folder), shareClasses);
    }

    /** Public data, read by both runtimes: the dealer app's catalog and the pipeline's fund boundaries. */
    @Bean
    ShareClasses shareClasses(@Value("${fundeli5.fixtures.dir}") String fixtures) {
        return new FixtureShareClasses(Path.of(fixtures, "share-classes.json"));
    }

    @Bean
    LlmEgress llmEgress(@Value("${fundeli5.llm.recordings}") String recordings) {
        return new ReplayLlmEgress(Path.of(recordings));
    }
}
