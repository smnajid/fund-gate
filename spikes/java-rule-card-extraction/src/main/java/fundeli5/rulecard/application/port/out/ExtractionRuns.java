package fundeli5.rulecard.application.port.out;

import java.nio.file.Path;

import fundeli5.rulecard.application.ExtractionRun;

/** Outbound port: keeps each run for scoring and for the validation queue. */
public interface ExtractionRuns {

    Path save(ExtractionRun run);
}
