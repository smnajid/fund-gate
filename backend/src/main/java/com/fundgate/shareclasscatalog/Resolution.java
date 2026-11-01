package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.util.List;

/**
 * What a fund or class mention, or an ISIN, resolves to. Only Resolved names Share Classes to check: a mention that
 * matches classes of several funds, or leaves the class of one fund in doubt, is Ambiguous, and the Fund Dealer is
 * asked for the ISIN or Valor, never handed classes as if the mention had named them.
 */
public sealed interface Resolution {

    /** The Share Classes of one fund the mention names, in catalog order; a fund named without a class gives all. */
    record Resolved(List<ShareClass> shareClasses) implements Resolution {

        public Resolved {
            shareClasses = List.copyOf(shareClasses);
            if (shareClasses.isEmpty()) {
                throw new IllegalArgumentException("A Resolved mention names at least one Share Class");
            }
        }
    }

    /**
     * Every fund the mention matches, each with the Share Classes it matched, in catalog order; or one fund whose class
     * the mention leaves in doubt, with its candidate classes.
     */
    record Ambiguous(List<CandidateFund> funds) implements Resolution {

        public Ambiguous {
            funds = List.copyOf(funds);
            if (funds.isEmpty()) {
                throw new IllegalArgumentException("An Ambiguous mention has at least one candidate fund");
            }
        }
    }

    /** The mention names no Share Class of the catalog: fund not covered, never a guess. */
    record NotFound() implements Resolution {
    }

    /** One fund an Ambiguous mention may mean, with its matched Share Classes and so their ISINs. */
    record CandidateFund(String fundName, List<ShareClass> shareClasses) {

        public CandidateFund {
            shareClasses = List.copyOf(shareClasses);
            if (shareClasses.isEmpty()) {
                throw new IllegalArgumentException("A candidate fund has at least one matched Share Class");
            }
        }
    }
}
