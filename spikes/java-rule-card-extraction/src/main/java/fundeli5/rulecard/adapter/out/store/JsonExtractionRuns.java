package fundeli5.rulecard.adapter.out.store;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import fundeli5.rulecard.application.ExtractionRun;
import fundeli5.rulecard.application.port.out.ExtractionRuns;

/** Writes {@code runs/<stamp>-<label>.json}. PROTOTYPE: files, no database. */
public class JsonExtractionRuns implements ExtractionRuns {

    /** snake_case, lowercase enums: the same wire shape as the Python spike's runs. */
    public static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .build();

    private final Path dir;

    public JsonExtractionRuns(Path dir) {
        this.dir = dir;
    }

    @Override
    public Path save(ExtractionRun run) {
        var stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        var path = dir.resolve(stamp + "-" + run.label() + ".json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), run);
        return path;
    }
}
