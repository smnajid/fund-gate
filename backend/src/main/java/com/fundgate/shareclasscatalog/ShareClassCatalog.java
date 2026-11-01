package com.fundgate.shareclasscatalog;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.shareclasscatalog.Resolution.Ambiguous;
import com.fundgate.shareclasscatalog.Resolution.CandidateFund;
import com.fundgate.shareclasscatalog.Resolution.NotFound;
import com.fundgate.shareclasscatalog.Resolution.Resolved;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasscatalog.ShareClassMatcher.FundMatch;
import com.fundgate.shareclasscatalog.ShareClassMatcher.IndexedClass;
import com.fundgate.shareclasses.ShareClasses;
import java.util.List;

/**
 * The Share Classes the bank distributes, read once and kept in memory, and the matcher that turns a fund or class
 * mention, or an ISIN, into the Share Classes it names (architecture.md, Share Class catalog). Deterministic, with no
 * LLM: a mention that matches nothing gives no Share Class, never a guess.
 */
public final class ShareClassCatalog {

    private final List<ShareClass> shareClasses;
    private final List<IndexedClass> indexed;

    /**
     * Throws {@link FundDataUnavailable} when the catalog cannot be read or is empty: an empty catalog would turn every
     * check into "fund not covered" (contracts/share-classes.md).
     */
    public ShareClassCatalog(ShareClasses source) {
        List<ShareClass> read = source.all();
        if (read == null) {
            throw new FundDataUnavailable("The Share Class catalog source gave no list", null);
        }
        this.shareClasses = List.copyOf(read);
        if (shareClasses.isEmpty()) {
            throw new FundDataUnavailable("The Share Class catalog is empty", null);
        }
        this.indexed = ShareClassMatcher.index(shareClasses);
    }

    /** Every Share Class of the catalog, in the order the port gave them. */
    public List<ShareClass> all() {
        return shareClasses;
    }

    /**
     * What a mention or ISIN names: the Share Classes of one fund the rule of {@link Resolved} allows, the candidate
     * funds when it matches several, the candidate classes when it leaves the class of one fund in doubt, or nothing.
     * Of several funds, the one whose whole name the mention gives is the one it means. A candidate fund is shown under
     * the name of its first class in catalog order, since classes sharing a fund id may carry different names. The
     * reverse-lookup search lists every match of the same matcher instead ({@link ShareClassMatcher#match}).
     */
    public Resolution resolve(String mentionOrIsin) {
        List<FundMatch> funds = ShareClassMatcher.matchByFund(mentionOrIsin, indexed);
        if (funds.isEmpty()) {
            return new NotFound();
        }
        List<FundMatch> namedInFull = funds.stream().filter(FundMatch::fundNamedInFull).toList();
        List<FundMatch> meant = namedInFull.size() == 1 ? namedInFull : funds;
        if (meant.size() == 1 && meant.getFirst().named()) {
            return new Resolved(meant.getFirst().shareClasses());
        }
        return new Ambiguous(meant.stream()
                .map(fund -> new CandidateFund(fund.fundName(), fund.shareClasses()))
                .toList());
    }
}
