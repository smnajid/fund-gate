package com.fundgate.rulecard.fixture;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.shareclasses.Isin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;

/** Reads the Rule Cards from a folder of fixture files, one card version per file. */
public class FixtureRuleCards implements RuleCards {

    private final Path folder;

    public FixtureRuleCards(Path folder) {
        this.folder = folder;
    }

    @Override
    public Optional<RuleCard> forShareClass(Isin shareClass) {
        List<RuleCard> covering = all().stream().filter(card -> card.shareClasses().contains(shareClass)).toList();
        // The latest Validated version decides while a newer one is still a Draft (Stale Rule Card, GLOSSARY.md)
        Optional<RuleCard> validated = covering.stream()
                .filter(card -> card.status() == RuleCard.Status.VALIDATED)
                .max(Comparator.comparingInt(RuleCard::version));
        return validated.or(() -> covering.stream().max(Comparator.comparingInt(RuleCard::version)));
    }

    private List<RuleCard> all() {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(file -> FixtureJson.read(file, RuleCard.class))
                    .toList();
        } catch (IOException | JacksonException e) {
            throw new FundDataUnavailable("Cannot read the Rule Card fixtures in " + folder, e);
        }
    }
}
