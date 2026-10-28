package com.fundgate.specs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

/**
 * The executable specification in specs/ runs. A scenario not built yet shows as undefined, or as failing on its
 * assertion once its steps are defined (build-workflow.md, S3); only an error in a step fails the build. A module's
 * own tag passing is its definition of done.
 */
class SpecificationRunsTest {

    private static final Path SPECS = Path.of("../specs");

    @Test
    void every_feature_of_specs_runs() throws IOException {
        Events scenarios = runSpecification();

        Set<String> featuresRun = scenarios.stream()
                .map(SpecificationRunsTest::featureOf)
                .filter(feature -> !feature.isEmpty())
                .collect(Collectors.toSet());
        assertThat(featuresRun).containsExactlyInAnyOrderElementsOf(featureFilesWithScenarios());
    }

    @Test
    void only_a_scenario_whose_step_errors_fails_the_build() {
        Events scenarios = runSpecification();

        assertThat(scenarios.count()).isPositive();
        List<String> broken = scenarios.stream()
                .filter(event -> !passed(event) && !isUndefined(event) && !failedOnAssertion(event))
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();
        assertThat(broken).isEmpty();
    }

    @Test
    void every_step_of_answering_a_subscription_request_is_defined() {
        List<String> undefined = runSpecification().stream()
                .filter(event -> featureOf(event).equals("02-answer-a-subscription-request.feature"))
                .filter(SpecificationRunsTest::isUndefined)
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();

        assertThat(undefined).isEmpty();
    }

    @Test
    void a_tag_filter_runs_only_that_modules_scenarios() {
        Events scenarios = runSpecification(Map.of("cucumber.filter.tags", "@rule-engine"));

        // 33 examples of 01-check-eligibility, 26 rows of 99-answer-key, 1 example of 02-answer-a-subscription-request
        assertThat(scenarios.count()).isEqualTo(60);
        assertThat(scenarios.stream())
                .allMatch(event -> event.getTestDescriptor().getTags().contains(TestTag.create("rule-engine")));
    }

    private static Events runSpecification() {
        return runSpecification(Map.of());
    }

    private static Events runSpecification(Map<String, String> configuration) {
        return EngineTestKit.engine("junit-platform-suite")
                .selectors(selectClass(SpecificationSuite.class))
                .configurationParameters(configuration)
                .execute()
                .testEvents()
                .finished();
    }

    private static String featureOf(Event scenario) {
        return scenario.getTestDescriptor().getSource()
                .filter(FileSource.class::isInstance)
                .map(source -> ((FileSource) source).getFile().getName())
                .orElse("");
    }

    private static boolean passed(Event finished) {
        return finished.getPayload(TestExecutionResult.class)
                .map(result -> result.getStatus() == TestExecutionResult.Status.SUCCESSFUL)
                .orElse(false);
    }

    private static boolean isUndefined(Event finished) {
        return finished.getPayload(TestExecutionResult.class)
                .flatMap(TestExecutionResult::getThrowable)
                .map(thrown -> thrown.getClass().getSimpleName().equals("UndefinedStepException"))
                .orElse(false);
    }

    private static boolean failedOnAssertion(Event finished) {
        return finished.getPayload(TestExecutionResult.class)
                .flatMap(TestExecutionResult::getThrowable)
                .map(AssertionError.class::isInstance)
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
