package fundeli5.rulecard.adapter.out.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import fundeli5.rulecard.application.port.out.FundDocuments;
import fundeli5.rulecard.domain.FundDocument;

/**
 * Fund Documentation from a local folder of PDFs ({@code <docId>.pdf}), page text extracted with PDFBox and
 * cached next to the PDF as {@code <docId>.pages.json} (the 944-page prospectus takes a while).
 */
public class PdfBoxFundDocuments implements FundDocuments {

    private final Path dir;
    private final Map<String, String> titles;
    private final JsonMapper json = JsonMapper.builder().build();

    public PdfBoxFundDocuments(Path dir, Map<String, String> titles) {
        this.dir = dir;
        this.titles = titles;
    }

    @Override
    public FundDocument load(String docId) {
        var cache = dir.resolve(docId + ".pages.json");
        List<String> pages;
        if (Files.exists(cache)) {
            pages = json.readValue(cache.toFile(), new TypeReference<List<String>>() {
            });
        } else {
            long t = System.nanoTime();
            pages = extract(docId);
            json.writeValue(cache.toFile(), pages);
            System.out.printf("text: %s, %d pages, %,d chars, %.1fs (cached)%n", docId, pages.size(),
                    pages.stream().mapToInt(String::length).sum(), (System.nanoTime() - t) / 1e9);
        }
        return new FundDocument(docId, titles.getOrDefault(docId, docId), pages);
    }

    @Override
    public byte[] excerpt(String docId, List<Integer> pages) {
        try (var src = open(docId); var out = new PDDocument(); var buf = new ByteArrayOutputStream()) {
            for (int p : pages) {
                out.importPage(src.getPage(p - 1));
            }
            out.save(buf);
            return buf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] whole(String docId) {
        try {
            return Files.readAllBytes(pdf(docId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> extract(String docId) {
        try (var doc = open(docId)) {
            var stripper = new PDFTextStripper();
            var pages = new ArrayList<String>(doc.getNumberOfPages());
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(stripper.getText(doc));
            }
            return pages;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PDDocument open(String docId) throws IOException {
        return Loader.loadPDF(pdf(docId).toFile());
    }

    private Path pdf(String docId) {
        return dir.resolve(docId + ".pdf");
    }
}
