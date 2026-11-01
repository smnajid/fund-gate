package com.fundgate.checkrecord;

import com.fundgate.checkrecord.memory.InMemoryDealerCorrections;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** The audit side of the Check Record: the corrections beside it, and the in port that reads both back. */
@Configuration
@Profile("!pipeline")
public class CheckRecordConfiguration {

    @Bean
    DealerCorrections dealerCorrections() {
        return new InMemoryDealerCorrections();
    }

    @Bean
    AuditChecks auditChecks(CheckRecords checkRecords, DealerCorrections dealerCorrections) {
        return new AuditChecks(checkRecords, dealerCorrections, Clock.systemUTC());
    }
}
