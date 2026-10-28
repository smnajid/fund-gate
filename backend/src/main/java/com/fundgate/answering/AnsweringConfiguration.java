package com.fundgate.answering;

import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.checkrecord.memory.InMemoryCheckRecords;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.privacygate.PrivacyGate;
import com.fundgate.privacygate.stub.StubPrivacyGate;
import com.fundgate.replydrafter.ReplyDrafter;
import com.fundgate.replydrafter.stub.StubReplyDrafter;
import com.fundgate.requestparser.RequestParser;
import com.fundgate.requestparser.stub.StubRequestParser;
import com.fundgate.ruleengine.RuleEngine;
import com.fundgate.ruleengine.stub.StubRuleEngine;
import java.time.Clock;
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
    RuleEngine ruleEngine() {
        return new StubRuleEngine();
    }

    @Bean
    ReplyDrafter replyDrafter() {
        return new StubReplyDrafter();
    }

    @Bean
    CheckRecords checkRecords() {
        return new InMemoryCheckRecords();
    }

    @Bean
    AnswerRequests answerRequests(ClientProfiles clientProfiles, PrivacyGate privacyGate, RequestParser requestParser,
            RuleEngine ruleEngine, ReplyDrafter replyDrafter, CheckRecords checkRecords) {
        return new AnswerRequests(clientProfiles, privacyGate, requestParser, ruleEngine, replyDrafter, checkRecords,
                Clock.systemUTC());
    }
}
