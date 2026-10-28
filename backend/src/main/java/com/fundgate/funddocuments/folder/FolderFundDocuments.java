package com.fundgate.funddocuments.folder;

import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.DocumentUnavailable;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.shareclasses.Isin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;

/** A manifest of documents and a folder of their PDFs. Inside the bank the manifest is generated from FMD's metadata. */
public class FolderFundDocuments implements FundDocuments {

    private final List<FundDocument> manifest;
    private final Path folder;

    public FolderFundDocuments(Path manifest, Path folder) {
        this.manifest = List.copyOf(FixtureJson.read(manifest, new TypeReference<List<FundDocument>>() {}));
        this.folder = folder;
    }

    @Override
    public List<FundDocument> documents(Isin anyShareClassOfTheFund) {
        // The prospectus lists every class of the fund, so the classes sharing a document with this one are its fund
        Set<Isin> fund = manifest.stream()
                .filter(document -> document.isins().contains(anyShareClassOfTheFund))
                .flatMap(document -> document.isins().stream())
                .collect(Collectors.toSet());
        return manifest.stream()
                .filter(document -> document.isins().stream().anyMatch(fund::contains))
                .toList();
    }

    @Override
    public byte[] content(DocumentId id) {
        FundDocument document = manifest.stream()
                .filter(candidate -> candidate.documentId().equals(id))
                .findFirst()
                .orElseThrow(() -> new DocumentUnavailable("No such document: " + id, null));
        String file = document.file()
                .orElseThrow(() -> new DocumentUnavailable("No file named for " + id, null));
        try {
            return Files.readAllBytes(folder.resolve(file));
        } catch (IOException e) {
            throw new DocumentUnavailable("Cannot read the PDF of " + id + " from " + folder, e);
        }
    }
}
