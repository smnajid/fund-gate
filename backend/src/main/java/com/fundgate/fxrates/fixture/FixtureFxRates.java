package com.fundgate.fxrates.fixture;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.fxrates.FxRate;
import com.fundgate.fxrates.FxRates;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

/** Reads the FX table from one fixture file, as written. */
public class FixtureFxRates implements FxRates {

    private final Path file;

    public FixtureFxRates(Path file) {
        this.file = file;
    }

    @Override
    public Optional<FxRate> rate(String base, String quote) {
        return read().stream()
                .filter(rate -> rate.base().equals(base) && rate.quote().equals(quote))
                .findFirst();
    }

    private List<FxRate> read() {
        try {
            return FixtureJson.read(file, new TypeReference<List<FxRate>>() {});
        } catch (JacksonException e) {
            throw new FundDataUnavailable("Cannot read the fixture " + file, e);
        }
    }
}
