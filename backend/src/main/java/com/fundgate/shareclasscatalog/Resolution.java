package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.util.List;

/**
 * What a fund or class mention, or an ISIN, resolves to. Only Resolved names Share Classes to check: a mention that
 * matches classes of several funds is Ambiguous, and the Fund Dealer is asked for the ISIN or Valor, never handed the
 * classes of several funds as if the mention had named them.
 */
public sealed interface Resolution {

    /** The Share Classes of one fund the mention names, in catalog order; a fund named without a class gives all. */
    record Resolved(List<ShareClass> shareClasses) implements Resolution {

        public Resolved {
            shareClasses = List.copyOf(shareClasses);
        }
    }

    /** Every fund the mention matches, each with the Share Classes it matched, in catalog order. */
    record Ambiguous(List<CandidateFund> funds) implements Resolution {

        public Ambiguous {
            funds = List.copyOf(funds);
        }
    }

    /** The mention names no Share Class of the catalog: fund not covered, never a guess. */
    record NotFound() implements Resolution {
    }

    /** One fund an Ambiguous mention may mean, with its matched Share Classes and so their ISINs. */
    record CandidateFund(String fundName, List<ShareClass> shareClasses) {

        public CandidateFund {
            shareClasses = List.copyOf(shareClasses);
        }
    }
}
