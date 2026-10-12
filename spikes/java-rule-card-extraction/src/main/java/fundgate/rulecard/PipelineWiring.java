package fundgate.rulecard;

import java.nio.file.Files;
import java.util.Map;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fundgate.rulecard.adapter.out.llm.claudeplan.ClaudePlanDrafter;
import fundgate.rulecard.adapter.out.llm.springai.SpringAiCitationsDrafter;
import fundgate.rulecard.adapter.out.pdf.PdfBoxFundDocuments;
import fundgate.rulecard.adapter.out.store.JsonExtractionRuns;
import fundgate.rulecard.application.DraftRuleCardService;
import fundgate.rulecard.application.port.in.DraftRuleCard;
import fundgate.rulecard.application.port.out.ExtractionRuns;
import fundgate.rulecard.application.port.out.FundDocuments;
import fundgate.rulecard.application.port.out.RuleCardDrafter;

/** The only place that knows which adapter plugs into which port. */
@Configuration
@EnableConfigurationProperties(PipelineProperties.class)
class PipelineWiring {

    @Bean
    FundDocuments fundDocuments(PipelineProperties p) {
        return new PdfBoxFundDocuments(p.docsDir(), Map.of(p.fund().prospectus(), p.fund().prospectusTitle()));
    }

    @Bean
    RuleCardDrafter ruleCardDrafter(PipelineProperties p, ObjectProvider<AnthropicChatModel> anthropic) {
        return switch (p.drafter()) {
            case "claude-plan" -> new ClaudePlanDrafter(p.extractModel());
            case "spring-ai" -> new SpringAiCitationsDrafter(anthropic.getObject(), p.extractModel(), p.normaliseModel());
            default -> throw new IllegalArgumentException("fundgate.drafter: " + p.drafter());
        };
    }

    @Bean
    ExtractionRuns extractionRuns(PipelineProperties p) throws java.io.IOException {
        return new JsonExtractionRuns(Files.createDirectories(p.runsDir()));
    }

    @Bean
    DraftRuleCard draftRuleCard(FundDocuments documents, RuleCardDrafter drafter, ExtractionRuns runs) {
        return new DraftRuleCardService(documents, drafter, runs);
    }
}
