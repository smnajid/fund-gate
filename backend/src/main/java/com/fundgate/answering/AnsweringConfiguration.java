package com.fundgate.answering;

import com.fundgate.checkrecord.RecordChecks;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.fundregistrations.FundRegistrations;
import com.fundgate.fxrates.FxRates;
import com.fundgate.privacygate.PrivacyGate;
import com.fundgate.privacygate.stub.StubPrivacyGate;
import com.fundgate.replydrafter.ReplyDrafter;
import com.fundgate.replydrafter.stub.StubReplyDrafter;
import com.fundgate.requestparser.RequestParser;
import com.fundgate.requestparser.stub.StubRequestParser;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.ruleengine.FixedRuleEngine;
import com.fundgate.ruleengine.RuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** The dealer app's modules, one bean each, so a ticket replaces one stub without touching the others (issue #57). */
@Configuration
@Profile("!pipeline")
public class AnsweringConfiguration {

    @Bean
    PrivacyGate privacyGate(ClientProfiles clientProfiles) {
        return new StubPrivacyGate(clientProfiles);
    }

    @Bean
    RequestParser requestParser() {
        return new StubRequestParser();
    }

    @Bean
    RuleEngine ruleEngine(RuleCards ruleCards, FundDocuments fundDocuments, FundRegistrations fundRegistrations,
            FxRates fxRates) {
        return new FixedRuleEngine(ruleCards, fundDocuments, fundRegistrations, fxRates);
    }

    @Bean
    ReplyDrafter replyDrafter() {
        return new StubReplyDrafter();
    }

    @Bean
    AnswerRequests answerRequests(ClientProfiles clientProfiles, PrivacyGate privacyGate, RequestParser requestParser,
            RuleEngine ruleEngine, ReplyDrafter replyDrafter, RecordChecks recordChecks) {
        return new AnswerRequests(clientProfiles, privacyGate, requestParser, ruleEngine, replyDrafter, recordChecks);
    }
}
