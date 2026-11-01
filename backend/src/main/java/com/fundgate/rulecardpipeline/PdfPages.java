package com.fundgate.rulecardpipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** The text of each page of a PDF, extracted with PDFBox: what page selection reads and quotes are checked on. */
final class PdfPages {

    private PdfPages() {
    }

    /** Page 1 first. A PDF with no text layer gives blank pages, and then no quote is found on them. */
    static List<String> read(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>(document.getNumberOfPages());
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
            return List.copyOf(pages);
        }
    }
}
