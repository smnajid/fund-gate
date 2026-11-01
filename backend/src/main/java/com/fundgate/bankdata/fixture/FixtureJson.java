package com.fundgate.bankdata.fixture;

import java.nio.file.Path;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;

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

    /** The same reader, also knowing these subtypes by name, e.g. the kinds of rule a card may hold. */
    public static JsonMapper knowing(Map<String, ? extends Class<?>> subtypes) {
        return JSON.rebuild()
                .registerSubtypes(subtypes.entrySet().stream()
                        .map(subtype -> new NamedType(subtype.getValue(), subtype.getKey()))
                        .toArray(NamedType[]::new))
                .build();
    }
}
