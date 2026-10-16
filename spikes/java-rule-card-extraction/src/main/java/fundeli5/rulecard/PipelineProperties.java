package fundeli5.rulecard;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code fundeli5.*} in application.yml. PROTOTYPE: a few funds, configured, not looked up. */
@ConfigurationProperties("fundeli5")
public record PipelineProperties(String drafter, Path docsDir, Path runsDir, String extractModel,
                                 String normaliseModel, String fundId, Map<String, Fund> funds) {

    /** The fund this run works on: {@code fundeli5.fund-id}. */
    public Fund fund() {
        var fund = funds.get(fundId);
        if (fund == null) {
            throw new IllegalArgumentException("fundeli5.fund-id: " + fundId + " is not one of " + funds.keySet());
        }
        return fund;
    }

    /** {@code gold}: the answer key the spike scores against. */
    public record Fund(String name, String displayName, String prospectus, String prospectusTitle, Path gold,
                       Map<String, String> shareClasses, Map<String, String> kids) {

        public Fund {
            kids = kids == null ? Map.of() : kids;
        }
    }
}
