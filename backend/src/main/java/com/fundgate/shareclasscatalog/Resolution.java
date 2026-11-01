package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.util.List;

/**
 * What a fund or class mention, or an ISIN, resolves to. Only Resolved names Share Classes to check: a mention that
 * matches classes of several funds, or leaves the class of one fund in doubt, is Ambiguous, and the Fund Dealer is
 * asked for the ISIN or Valor, never handed classes as if the mention had named them.
 */
public sealed interface Resolution {

    /**
     * The Share Classes of one fund the mention names, in catalog order. Words of the fund or umbrella name never count
     * toward a class name, whichever class of the fund carries them (a fund id may group classes under different fund
     * names, a fund name classes under different umbrellas): a class is named only when the typed words left once
     * those are taken (counted: a word typed more often than any one class's fund and umbrella names use it keeps the
     * extra) hold its whole class name, with a word some other class of the fund does not carry. So with fund id F1
     * over "Bond Fund" class I and "Bond Fund I" class R, "Bond Fund I" is every class of the fund, not class I. Kept
     * by the matcher and checked over generated catalogs:
     * <ul>
     * <li>every class of the fund when no typed word is left but an ISIN or Valor prefix they all share, and the
     * mention matches each class;</li>
     * <li>the one class an ISIN or Valor prefix pins;</li>
     * <li>the one named class every other named class's name is part of, when no class whose name is typed in full,
     * fund words included, has the same name or a longer one containing it;</li>
     * <li>never classes of two funds: a mention matching several funds is {@link Ambiguous}.</li>
     * </ul>
     * Everything else that matches is {@link Ambiguous}: a word left that names no class (a currency or fund type every
     * class shares, a class name typed in part or only with the fund's words), or two class names typed.
     */
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
