package com.fundgate.specs.ruleengine;

import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.shareclasses.Isin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The fixture manifest, with the editions a scenario's fund master data lists instead; forgotten before each scenario. */
public class ListedEditions implements FundDocuments {

    private final FundDocuments fixtures;
    private final Map<DocumentId, String> fmdEditions = new HashMap<>();

    public ListedEditions(FundDocuments fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public List<FundDocument> documents(Isin anyShareClassOfTheFund) {
        return fixtures.documents(anyShareClassOfTheFund).stream()
                .map(document -> Optional.ofNullable(fmdEditions.get(document.documentId()))
                        .map(edition -> new FundDocument(document.documentId(), document.isins(), document.type(),
                                document.language(), Optional.of(edition), document.pdfEditionDate(),
                                document.confidentiality(), document.contentSha256(), document.sourceUrl(),
                                document.file()))
                        .orElse(document))
                .toList();
    }

    @Override
    public byte[] content(DocumentId id) {
        return fixtures.content(id);
    }

    public void list(DocumentId document, String fmdEdition) {
        fmdEditions.put(document, fmdEdition);
    }

    public void reset() {
        fmdEditions.clear();
    }
}
