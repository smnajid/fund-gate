package fundeli5.rulecard;

import java.nio.file.Files;
import java.util.Map;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fundeli5.rulecard.adapter.out.jev.JevClient;
import fundeli5.rulecard.adapter.out.jev.JevPageRanking;
import fundeli5.rulecard.adapter.out.llm.claudeplan.ClaudePlanDrafter;
import fundeli5.rulecard.adapter.out.llm.springai.SpringAiCitationsDrafter;
import fundeli5.rulecard.adapter.out.pdf.PdfBoxFundDocuments;
import fundeli5.rulecard.adapter.out.store.JsonExtractionRuns;
import fundeli5.rulecard.application.DraftRuleCardService;
import fundeli5.rulecard.application.port.in.DraftRuleCard;
import fundeli5.rulecard.application.port.out.ExtractionRuns;
import fundeli5.rulecard.application.port.out.FundDocuments;
import fundeli5.rulecard.application.port.out.PageRanking;
import fundeli5.rulecard.application.port.out.RuleCardDrafter;

/** The only place that knows which adapter plugs into which port. */
@Configuration
@EnableConfigurationProperties(PipelineProperties.class)
class PipelineWiring {

    @Bean
    FundDocuments fundDocuments(PipelineProperties p) {
        return new PdfBoxFundDocuments(p.docsDir(), Map.of(p.fund().prospectus(), p.fund().prospectusTitle()));
    }

    @Bean
    PageRanking pageRanking(PipelineProperties p) {
        return new JevPageRanking(new JevClient(p.jev().model(), p.jev().concurrency()), p.jev().cacheDir());
    }

    @Bean
    RuleCardDrafter ruleCardDrafter(PipelineProperties p, ObjectProvider<AnthropicChatModel> anthropic) {
        return switch (p.drafter()) {
            case "claude-plan" -> new ClaudePlanDrafter(p.extractModel());
            case "spring-ai" -> new SpringAiCitationsDrafter(anthropic.getObject(), p.extractModel(), p.normaliseModel());
            default -> throw new IllegalArgumentException("fundeli5.drafter: " + p.drafter());
        };
    }

    @Bean
    ExtractionRuns extractionRuns(PipelineProperties p) throws java.io.IOException {
        return new JsonExtractionRuns(Files.createDirectories(p.runsDir()));
    }

    @Bean
    DraftRuleCard draftRuleCard(FundDocuments documents, PageRanking ranking, RuleCardDrafter drafter,
            ExtractionRuns runs) {
        return new DraftRuleCardService(documents, ranking, drafter, runs);
    }
}
