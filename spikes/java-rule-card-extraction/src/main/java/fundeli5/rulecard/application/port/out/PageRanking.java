package fundeli5.rulecard.application.port.out;

import java.util.List;

import fundeli5.rulecard.domain.FundDocument;

/** Outbound port: which pages of a long prospectus the drafter reads, and which rule kinds they may have missed. */
public interface PageRanking {

    /** At most {@code maxPages} 1-based pages; every page of a document no longer than that. */
    List<Integer> select(FundDocument doc, String subFund, int maxPages);

    /** Per rule kind, how well the selected pages cover it, for "possibly missed" on the validation screen. */
    List<Coverage> coverage(FundDocument doc, String subFund, List<Integer> selected);

    /**
     * {@code bestSelected} is null when no selected page was scored; {@code bestLeftOut} is null when the best page
     * overall was selected.
     */
    record Coverage(String kind, Scored bestSelected, Scored bestLeftOut, boolean possiblyMissed) {
    }

    record Scored(int page, double score) {
    }
}
