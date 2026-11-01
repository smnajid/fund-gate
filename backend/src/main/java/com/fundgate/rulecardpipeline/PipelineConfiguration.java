package com.fundgate.rulecardpipeline;

import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.llm.LlmEgress;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClasses;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;

/**
 * The offline runtime (ADR 0004): drafts the Rule Card of each fund named, writes each Draft card for a Rule Card
 * Validator, and says why a fund got none. Public data only: this profile has no Client Profile adapter at all.
 * {@code --fundeli5.pipeline.share-classes=LU0348926287,...} names one Share Class per fund.
 */
@Configuration
@Profile("pipeline")
public class PipelineConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineConfiguration.class);

    private static final JsonMapper JSON = FixtureJson.knowing(RuleCatalog.conditionTypes());

    @Bean
    RuleCardPipeline ruleCardPipeline(FundDocuments documents, ShareClasses shareClasses, LlmEgress llm,
            @Value("${fundeli5.pipeline.model}") String model, @Value("${fundeli5.llm.endpoint}") String endpoint) {
        return new RuleCardPipeline(documents, shareClasses, llm, model, endpoint, Clock.systemUTC());
    }

    @Bean
    ApplicationRunner draftRuleCards(RuleCardPipeline pipeline,
            @Value("${fundeli5.pipeline.share-classes:}") List<String> shareClasses,
            @Value("${fundeli5.pipeline.out}") String out) {
        return arguments -> {
            for (String isin : shareClasses) {
                try {
                    draftAndWrite(pipeline, new Isin(isin.strip()), Path.of(out));
                } catch (RuntimeException e) {
                    // One mistyped ISIN or unwritable file stops that fund only
                    LOG.warn("No Rule Card for {}: {}", isin, e.getMessage());
                }
            }
        };
    }

    private static void draftAndWrite(RuleCardPipeline pipeline, Isin isin, Path out) {
        switch (pipeline.draft(isin)) {
            case Drafted.Draft draft -> {
                Path file = write(draft.card(), out);
                LOG.info("Drafted {} rules for {} into {}; {} quotes not found on their page, {} rules without a "
                        + "quote, {} rules not read", draft.card().rules().size(), draft.card().fundName(), file,
                        draft.quotesNotFound().size(), draft.rulesWithoutQuote().size(), draft.rulesNotRead().size());
                draft.rulesNotRead().forEach(reason -> LOG.warn("Not read, for the validator to add: {}", reason));
            }
            case Drafted.Refused refused -> LOG.warn("No Rule Card for {}: {}", isin.value(), refused.reason());
        }
    }

    private static Path write(RuleCard card, Path folder) {
        Path file = folder.resolve(card.fundName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                + "-v" + card.version() + "-draft.json");
        try {
            Files.createDirectories(folder);
            // A validator may have started on an earlier draft: it is never overwritten
            Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(card),
                    StandardOpenOption.CREATE_NEW);
            return file;
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("A Draft Rule Card is already at " + file + "; move it away first", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the Draft Rule Card to " + file, e);
        }
    }
}
