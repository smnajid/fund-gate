package com.fundgate.specs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

/** The executable specification in specs/ runs, and a step nobody has written yet shows as undefined, not as an error. */
class SpecificationRunsTest {

    private static final Path SPECS = Path.of("../specs");

    @Test
    void every_feature_of_specs_runs() throws IOException {
        Events scenarios = runSpecification();

        Set<String> featuresRun = scenarios.stream()
                .map(event -> event.getTestDescriptor().getSource())
                .flatMap(source -> source.stream())
                .filter(FileSource.class::isInstance)
                .map(source -> ((FileSource) source).getFile().getName())
                .collect(Collectors.toSet());
        assertThat(featuresRun).containsExactlyInAnyOrderElementsOf(featureFilesWithScenarios());
    }

    @Test
    void a_scenario_with_unwritten_steps_is_undefined_not_an_error() {
        Events scenarios = runSpecification();

        assertThat(scenarios.count()).isPositive();
        List<String> notUndefined = scenarios.stream()
                .filter(event -> !isUndefined(event))
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();
        assertThat(notUndefined).isEmpty();
    }

    private static Events runSpecification() {
        return EngineTestKit.engine("junit-platform-suite")
                .selectors(selectClass(SpecificationSuite.class))
                .execute()
                .testEvents()
                .finished();
    }

    private static boolean isUndefined(Event finished) {
        return finished.getPayload(TestExecutionResult.class)
                .flatMap(TestExecutionResult::getThrowable)
                .map(thrown -> thrown.getClass().getSimpleName().equals("UndefinedStepException"))
                .orElse(false);
    }

    /** 00-fundeli5.feature only describes the product; it has no scenario to run. */
    private static Set<String> featureFilesWithScenarios() throws IOException {
        try (Stream<Path> files = Files.list(SPECS)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".feature"))
                    .filter(SpecificationRunsTest::hasScenario)
                    .map(file -> file.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }

    private static boolean hasScenario(Path feature) {
        try (Stream<String> lines = Files.lines(feature)) {
            return lines.anyMatch(line -> line.strip().matches("(Scenario|Scenario Outline|Example):.*"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
