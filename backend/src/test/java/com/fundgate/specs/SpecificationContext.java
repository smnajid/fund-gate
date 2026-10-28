package com.fundgate.specs;

import com.fundgate.llm.replay.ReplayLlmEgress;
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
}
