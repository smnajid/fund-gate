package com.fundgate.validation;

import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.DocumentUnavailable;
import com.fundgate.funddocuments.FundDocuments;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * The extracted text of a cited page, which the validation screen shows with the quote highlighted (screens.md).
 * Extracted as the pipeline's quote check reads it, with PDFBox; kept once read, as a prospectus is large.
 */
public class PageText {

    private record Page(DocumentId document, int page) {
    }

    private final FundDocuments documents;
    private final Map<Page, Optional<String>> read = new ConcurrentHashMap<>();

    public PageText(FundDocuments documents) {
        this.documents = documents;
    }

    /** Empty when the document's file is not here or has no such page: the screen then shows the quote alone. */
    public Optional<String> of(DocumentId document, int page) {
        return read.computeIfAbsent(new Page(document, page), this::extract);
    }

    private Optional<String> extract(Page cited) {
        try (PDDocument pdf = Loader.loadPDF(documents.content(cited.document()))) {
            if (cited.page() < 1 || cited.page() > pdf.getNumberOfPages()) {
                return Optional.empty();
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(cited.page());
            stripper.setEndPage(cited.page());
            return Optional.of(stripper.getText(pdf));
        } catch (DocumentUnavailable | IOException e) {
            return Optional.empty();
        }
    }
}
