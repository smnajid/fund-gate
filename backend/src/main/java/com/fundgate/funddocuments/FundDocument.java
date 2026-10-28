package com.fundgate.funddocuments;

import com.fundgate.shareclasses.Isin;
import java.util.List;
import java.util.Optional;

/** One document of a fund's Fund Documentation (contracts/fund-documents.md). */
public record FundDocument(
        DocumentId documentId,
        /** Every class of the fund for a prospectus, one for a KID. */
        List<Isin> isins,
        Optional<Type> type,
        Optional<String> language,
        /** A date or YYYY-MM. Absent outside the bank. */
        Optional<String> fmdEditionDate,
        /** A date or YYYY-MM, as printed in the PDF. */
        Optional<String> pdfEditionDate,
        /** Absent: treated as confidential. */
        Optional<Confidentiality> confidentiality,
        String contentSha256,
        Optional<String> sourceUrl,
        /** The PDF's file name. Folder adapter only. */
        Optional<String> file) {

    public enum Type { PROSPECTUS, KID, SUPPLEMENT, FUND_CONTRACT, PPM, OTHER }

    public enum Confidentiality { PUBLIC, CONFIDENTIAL }
}
