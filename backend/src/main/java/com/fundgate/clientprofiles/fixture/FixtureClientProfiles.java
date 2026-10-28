package com.fundgate.clientprofiles.fixture;

import com.fundgate.bankdata.fixture.FixtureJson;
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

/** Reads one fixture file per Portfolio Reference, as written: {@code <directory>/<reference>.json}. */
public class FixtureClientProfiles implements ClientProfiles {

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
        // A missing folder is the bank's data being unavailable, not every portfolio being unknown
        if (!Files.isDirectory(directory)) {
            throw new ClientDataUnavailable("No Client Profile fixtures in " + directory, null);
        }
        Path file = directory.resolve(ref.value() + ".json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            ClientProfile profile = FixtureJson.read(file, ClientProfile.class);
            if (!profile.portfolioRef().equals(ref)) {
                throw new ClientDataUnavailable("The Client Profile fixture of " + ref + " holds another portfolio", null);
            }
            return Optional.of(profile);
        } catch (JacksonException e) {
            // The file name holds the reference, so the message gives the masked one
            throw new ClientDataUnavailable("Cannot read the Client Profile fixture of " + ref + " in " + directory, e);
        }
    }
}
