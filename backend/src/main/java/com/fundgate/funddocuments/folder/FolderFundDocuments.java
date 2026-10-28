package com.fundgate.funddocuments.folder;

import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.DocumentUnavailable;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.ShareClasses;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;

/** A manifest of documents and a folder of their PDFs. Inside the bank the manifest is generated from FMD's metadata. */
public class FolderFundDocuments implements FundDocuments {

    private final List<FundDocument> manifest;
    private final Path folder;
    private final ShareClasses catalog;

    /** The catalog says which classes make up a fund; a document's own ISIN list can span an umbrella's sub-funds. */
    public FolderFundDocuments(Path manifest, Path folder, ShareClasses catalog) {
        this.manifest = List.copyOf(FixtureJson.read(manifest, new TypeReference<List<FundDocument>>() {}));
        this.folder = folder;
        this.catalog = catalog;
    }

    @Override
    public List<FundDocument> documents(Isin anyShareClassOfTheFund) {
        Set<Isin> fund = classesOfTheSameFund(anyShareClassOfTheFund);
        return manifest.stream()
                .filter(document -> document.isins().stream().anyMatch(fund::contains))
                .toList();
    }

    /** Same fundId when the catalog has one, else the same fund (sub-fund) name. */
    private Set<Isin> classesOfTheSameFund(Isin isin) {
        List<ShareClass> classes = catalog.all();
        return classes.stream()
                .filter(shareClass -> shareClass.isin().equals(isin))
                .findFirst()
                .map(asked -> classes.stream()
                        .filter(other -> asked.fundId().isPresent()
                                ? asked.fundId().equals(other.fundId())
                                : Objects.equals(asked.fundName(), other.fundName()))
                        .map(ShareClass::isin)
                        .collect(Collectors.toSet()))
                .orElse(Set.of(isin));
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
