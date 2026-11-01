package com.fundgate.validation;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleCard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Reads a folder of Rule Card files, one card version per file, as the fixtures and the pipeline write them. */
public final class RuleCardFiles {

    private final JsonMapper json;

    /** The condition of each kind of rule, by its name in a card's JSON (com.fundgate.ruleengine.RuleCatalog). */
    public RuleCardFiles(Map<String, Class<? extends Condition>> conditionTypes) {
        this.json = FixtureJson.knowing(conditionTypes);
    }

    /** In file name order; a folder that does not exist holds no card, e.g. before the pipeline first ran. */
    public List<RuleCard> read(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(file -> json.readValue(file.toFile(), RuleCard.class))
                    .toList();
        } catch (IOException | JacksonException e) {
            throw new FundDataUnavailable("Cannot read the Rule Cards in " + folder, e);
        }
    }
}
