package com.fundgate.validation;

import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.ruleengine.RuleCatalog;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** The validation screen's module, in the dealer app; the Rule Cards it works on are the engine's (AdaptersConfiguration). */
@Configuration
@Profile("!pipeline")
public class ValidationConfiguration {

    @Bean
    RuleCardValidation ruleCardValidation(RuleCardVersions ruleCards) {
        return new RuleCardValidation(ruleCards, RuleCatalog.conditionTypes(), Clock.systemUTC());
    }

    @Bean
    PageText pageText(FundDocuments fundDocuments) {
        return new PageText(fundDocuments);
    }
}
