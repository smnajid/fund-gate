package com.fundgate.specs;

import com.fundgate.funddocuments.folder.FolderFundDocuments;
import com.fundgate.llm.replay.ReplayLlmEgress;
import com.fundgate.rulecard.fixture.FixtureRuleCards;
import com.fundgate.shareclasses.ShareClasses;
import com.fundgate.specs.ruleengine.EditableRuleCards;
import com.fundgate.specs.ruleengine.ListedEditions;
import io.cucumber.spring.CucumberContextConfiguration;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** The one Spring context every module's step definitions share. */
@CucumberContextConfiguration
@SpringBootTest
public class SpecificationContext {

    @TestConfiguration
    static class LlmCalls {

        @Bean
        @Primary
        RecordingLlmEgress recordingLlmEgress(@Value("${fundeli5.llm.recordings}") String recordings) {
            return new RecordingLlmEgress(new ReplayLlmEgress(Path.of(recordings)));
        }
    }

    /** What a scenario's Rule Card Validator and fund master data change, over the fixtures. */
    @TestConfiguration
    static class RuleCardsAndEditions {

        @Bean
        @Primary
        EditableRuleCards editableRuleCards(@Value("${fundeli5.fixtures.dir}") String fixtures) {
            return new EditableRuleCards(new FixtureRuleCards(Path.of(fixtures, "rule-cards")));
        }

        @Bean
        @Primary
        ListedEditions listedEditions(@Value("${fundeli5.fixtures.dir}") String fixtures,
                @Value("${fundeli5.documents.folder}") String folder, ShareClasses shareClasses) {
            return new ListedEditions(
                    new FolderFundDocuments(Path.of(fixtures, "fund-documents.json"), Path.of(folder), shareClasses));
        }
    }
}
