package com.fundgate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.fundregistrations.FundRegistrations;
import com.fundgate.llm.LlmEgress;
import com.fundgate.shareclasses.ShareClasses;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Two runtimes, one app (ADR 0004): the dealer app serves HTTP, the pipeline runs as a command-line process. */
class RuntimeProfilesTest {

    @Test
    void the_dealer_app_starts_a_web_server() {
        try (ConfigurableApplicationContext app = start()) {
            assertThat(app).isInstanceOf(WebServerApplicationContext.class);
        }
    }

    @Test
    void the_pipeline_profile_starts_with_no_web_server() {
        try (ConfigurableApplicationContext pipeline = start("pipeline")) {
            assertThat(pipeline).isNotInstanceOf(WebServerApplicationContext.class);
        }
    }

    @Test
    void the_dealer_app_reads_client_profiles_share_classes_and_fund_registrations() {
        try (ConfigurableApplicationContext app = start()) {
            assertThat(app.getBeanNamesForType(ClientProfiles.class)).hasSize(1);
            assertThat(app.getBeanNamesForType(ShareClasses.class)).hasSize(1);
            assertThat(app.getBeanNamesForType(FundRegistrations.class)).hasSize(1);
            assertThat(app.getBeanNamesForType(LlmEgress.class)).hasSize(1);
        }
    }

    @Test
    void the_pipeline_reads_fund_documents_and_never_a_client_profile() {
        try (ConfigurableApplicationContext pipeline = start("pipeline")) {
            assertThat(pipeline.getBeanNamesForType(FundDocuments.class)).hasSize(1);
            assertThat(pipeline.getBeanNamesForType(LlmEgress.class)).hasSize(1);
            assertThat(pipeline.getBeanNamesForType(ClientProfiles.class)).isEmpty();
        }
    }

    private static ConfigurableApplicationContext start(String... profiles) {
        return new SpringApplicationBuilder(FundEli5Application.class)
                .profiles(profiles)
                // The platform reads its own frozen fixtures, never the demo content the team rewrites (#80)
                .run("--server.port=0", "--fundeli5.fixtures.dir=src/test/resources/fixtures");
    }
}
