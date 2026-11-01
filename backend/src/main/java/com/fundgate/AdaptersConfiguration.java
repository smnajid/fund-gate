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
import com.fundgate.rulecard.RuleCard;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.shareclasses.ShareClasses;
import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import com.fundgate.validation.RuleCardFiles;
import com.fundgate.validation.RuleCardVersions;
import java.nio.file.Path;
import java.util.List;
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

        /**
         * The Rule Cards the engine reads and the validator works on: the card files as they are numbered, then each
         * Draft the pipeline wrote, numbered after its fund's latest version.
         */
        @Bean
        RuleCardVersions ruleCards(@Value("${fundeli5.fixtures.dir}") String fixtures,
                @Value("${fundeli5.validation.drafts}") List<String> drafts) {
            RuleCardFiles files = new RuleCardFiles(RuleCatalog.conditionTypes());
            RuleCardVersions versions = new RuleCardVersions(files.read(Path.of(fixtures, "rule-cards")));
            drafts.stream().map(Path::of).map(files::read).flatMap(List::stream)
                    .filter(card -> card.status() == RuleCard.Status.DRAFT)
                    .forEach(versions::addDraft);
            return versions;
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
