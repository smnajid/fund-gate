package com.fundgate;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static ConfigurableApplicationContext start(String... profiles) {
        return new SpringApplicationBuilder(FundEli5Application.class)
                .profiles(profiles)
                .run("--server.port=0");
    }
}
