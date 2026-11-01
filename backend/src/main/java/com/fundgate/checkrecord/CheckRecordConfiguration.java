package com.fundgate.checkrecord;

import com.fundgate.checkrecord.memory.InMemoryCheckRecords;
import com.fundgate.checkrecord.memory.InMemoryDealerCorrections;
import com.fundgate.rulecard.RuleCards;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** The Check Records and the corrections beside them, the in port that writes a record and the one that reads both back. */
@Configuration
@Profile("!pipeline")
public class CheckRecordConfiguration {

    /** The engine version of a build with no build-info, e.g. run from an IDE without Maven. */
    static final String DEV_BUILD = "dev";

    @Bean
    CheckRecords checkRecords() {
        return new InMemoryCheckRecords();
    }

    @Bean
    DealerCorrections dealerCorrections() {
        return new InMemoryDealerCorrections();
    }

    /**
     * The engine version is the application build, read from META-INF/build-info.properties, which the
     * spring-boot-maven-plugin's build-info goal writes on every Maven build, tests included.
     */
    @Bean
    RecordChecks recordChecks(CheckRecords checkRecords, RuleCards ruleCards, ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        String engineVersion = properties == null
                ? DEV_BUILD
                : properties.getVersion() + " built " + properties.getTime();
        return new RecordChecks(checkRecords, ruleCards, engineVersion, Clock.systemUTC());
    }

    @Bean
    AuditChecks auditChecks(CheckRecords checkRecords, DealerCorrections dealerCorrections) {
        return new AuditChecks(checkRecords, dealerCorrections, Clock.systemUTC());
    }
}
