package com.fundgate.fundregistrations.fixture;

import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.fundregistrations.FundRegistration;
import com.fundgate.fundregistrations.FundRegistrations;
import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.shareclasses.Isin;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

/** Reads the Fund Registration table from one fixture file, as written. A class not in the file has no list: Unknown. */
public class FixtureFundRegistrations implements FundRegistrations {

    private final Path file;

    public FixtureFundRegistrations(Path file) {
        this.file = file;
    }

    @Override
    public Optional<FundRegistration> fundRegistration(Isin isin) {
        return read().stream()
                .filter(fundRegistration -> fundRegistration.isin().equals(isin))
                .findFirst();
    }

    private List<FundRegistration> read() {
        try {
            return FixtureJson.read(file, new TypeReference<List<FundRegistration>>() {});
        } catch (JacksonException e) {
            throw new FundDataUnavailable("Cannot read the fixture " + file, e);
        }
    }
}
