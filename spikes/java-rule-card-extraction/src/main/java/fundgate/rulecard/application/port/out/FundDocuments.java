package fundgate.rulecard.application.port.out;

import java.util.List;

import fundgate.rulecard.domain.FundDocument;

/** Outbound port: where Fund Documentation comes from. */
public interface FundDocuments {

    FundDocument load(String docId);

    /** A PDF holding only the given 1-based pages of the document, in that order. */
    byte[] excerpt(String docId, List<Integer> pages);

    byte[] whole(String docId);
}
