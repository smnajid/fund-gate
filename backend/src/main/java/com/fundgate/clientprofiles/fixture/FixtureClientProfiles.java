package com.fundgate.clientprofiles.fixture;

import com.fundgate.clientprofiles.ClientDataUnavailable;
import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioNotFound;
import com.fundgate.clientprofiles.PortfolioReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** Reads one fixture file per Portfolio Reference, as written: {@code <directory>/<reference>.json}. */
public class FixtureClientProfiles implements ClientProfiles {

    // A field outside the contract is refused: ignoring a typo would leave a fact unknown without anyone noticing
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Path directory;

    public FixtureClientProfiles(Path directory) {
        this.directory = directory;
    }

    @Override
    public Optional<PortfolioId> portfolioId(PortfolioReference ref) {
        return read(ref).map(ClientProfile::portfolioId);
    }

    @Override
    public ClientProfile clientProfile(PortfolioReference ref) {
        return read(ref).orElseThrow(() -> new PortfolioNotFound(ref));
    }

    private Optional<ClientProfile> read(PortfolioReference ref) {
        Path file = directory.resolve(ref.value() + ".json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.readValue(file.toFile(), ClientProfile.class));
        } catch (JacksonException e) {
            throw new ClientDataUnavailable("Cannot read the fixture " + file, e);
        }
    }
}
