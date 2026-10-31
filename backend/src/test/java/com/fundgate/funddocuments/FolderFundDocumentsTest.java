package com.fundgate.funddocuments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.funddocuments.folder.FolderFundDocuments;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClasses;
import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The FundDocuments port over the sample documents' manifest (contracts/fund-documents.md). */
class FolderFundDocumentsTest {

    private static final Path MANIFEST = Path.of("src/test/resources/fixtures/fund-documents.json");
    private static final Isin GCE_BI = new Isin("LU0348927095");
    private static final ShareClasses CATALOG =
            new FixtureShareClasses(Path.of("src/test/resources/fixtures/share-classes.json"));

    @TempDir
    Path folder;

    @Test
    void any_class_of_a_fund_gives_every_document_of_that_fund() {
        FundDocuments fundDocuments = new FolderFundDocuments(MANIFEST, folder, CATALOG);

        assertThat(fundDocuments.documents(GCE_BI))
                .extracting(document -> document.documentId().value())
                .containsExactlyInAnyOrder("nordea1-prospectus-2026-06", "nordea1-gce-kid-bp-eur", "nordea1-gce-kid-bi-eur");
    }

    @Test
    void an_umbrella_prospectus_does_not_bring_in_another_sub_funds_kids() {
        FundDocuments umbrella = new FolderFundDocuments(Path.of("src/test/resources/umbrella/fund-documents.json"), folder,
                new FixtureShareClasses(Path.of("src/test/resources/umbrella/share-classes.json")));

        assertThat(umbrella.documents(new Isin("LU0348926287")))
                .extracting(document -> document.documentId().value())
                .containsExactlyInAnyOrder("nordea1-umbrella-prospectus", "nordea1-gce-kid-bp-eur");
    }

    @Test
    void the_content_is_the_pdf_the_manifest_names() throws IOException {
        Files.write(folder.resolve("prospectus.pdf"), new byte[] {'%', 'P', 'D', 'F'});
        FundDocuments fundDocuments = new FolderFundDocuments(MANIFEST, folder, CATALOG);

        assertThat(fundDocuments.content(new DocumentId("nordea1-prospectus-2026-06")))
                .containsExactly('%', 'P', 'D', 'F');
    }

    @Test
    void a_pdf_missing_from_the_folder_is_unavailable() {
        FundDocuments fundDocuments = new FolderFundDocuments(MANIFEST, folder, CATALOG);

        assertThatThrownBy(() -> fundDocuments.content(new DocumentId("nordea1-prospectus-2026-06")))
                .isInstanceOf(DocumentUnavailable.class)
                .hasMessageContaining("nordea1-prospectus-2026-06");
    }
}
