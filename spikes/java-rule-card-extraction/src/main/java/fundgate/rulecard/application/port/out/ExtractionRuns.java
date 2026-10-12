package fundgate.rulecard.application.port.out;

import java.nio.file.Path;

import fundgate.rulecard.application.ExtractionRun;

/** Outbound port: keeps each run for scoring and for the validation queue. */
public interface ExtractionRuns {

    Path save(ExtractionRun run);
}
