package com.fundgate.bankdata.fixture;

import java.nio.file.Path;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** Reads a fixture file into its canonical model, as the contract writes it. */
public final class FixtureJson {

    // A field outside the contract is refused: ignoring a typo would leave a fact unknown without anyone noticing
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private FixtureJson() {
    }

    /** Throws {@link tools.jackson.core.JacksonException} when the file is unreadable or outside the contract. */
    public static <T> T read(Path file, Class<T> type) {
        return JSON.readValue(file.toFile(), type);
    }

    /** Throws {@link tools.jackson.core.JacksonException} when the file is unreadable or outside the contract. */
    public static <T> T read(Path file, TypeReference<T> type) {
        return JSON.readValue(file.toFile(), type);
    }
}
