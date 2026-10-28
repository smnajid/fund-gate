package com.fundgate.shareclasses.fixture;

import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.shareclasses.FundDataUnavailable;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.ShareClasses;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

/** Reads the catalog from one fixture file, as written. */
public class FixtureShareClasses implements ShareClasses {

    private final Path file;

    public FixtureShareClasses(Path file) {
        this.file = file;
    }

    @Override
    public List<ShareClass> all() {
        try {
            return List.copyOf(FixtureJson.read(file, new TypeReference<List<ShareClass>>() {}));
        } catch (JacksonException e) {
            throw new FundDataUnavailable("Cannot read the fixture " + file, e);
        }
    }
}
