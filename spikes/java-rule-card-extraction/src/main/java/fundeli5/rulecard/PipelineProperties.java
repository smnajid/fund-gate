package fundeli5.rulecard;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code fundeli5.*} in application.yml. PROTOTYPE: one fund, configured, not looked up. */
@ConfigurationProperties("fundeli5")
public record PipelineProperties(String drafter, Path docsDir, Path runsDir, String extractModel,
                                 String normaliseModel, Fund fund) {

    public record Fund(String name, String displayName, String prospectus, String prospectusTitle,
                       Map<String, String> shareClasses, Map<String, String> kids) {
    }
}
