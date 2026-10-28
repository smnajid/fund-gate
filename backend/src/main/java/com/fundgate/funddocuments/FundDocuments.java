package com.fundgate.funddocuments;

import com.fundgate.shareclasses.Isin;
import java.util.List;

/** Out port: the Fund Documentation the offline pipeline drafts Rule Cards from (contracts/fund-documents.md). */
public interface FundDocuments {

    /** Every document of the fund the Share Class belongs to: its prospectus and every KID. */
    List<FundDocument> documents(Isin anyShareClassOfTheFund);

    /** The PDF. Throws {@link DocumentUnavailable}. */
    byte[] content(DocumentId id);
}
