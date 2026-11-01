package com.fundgate.shareclasscatalog;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.shareclasscatalog.Resolution.Ambiguous;
import com.fundgate.shareclasscatalog.Resolution.CandidateFund;
import com.fundgate.shareclasscatalog.Resolution.NotFound;
import com.fundgate.shareclasscatalog.Resolution.Resolved;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.ShareClasses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Share Classes the bank distributes, read once and kept in memory, and the matcher that turns a fund or class
 * mention, or an ISIN, into the Share Classes it names (architecture.md, Share Class catalog). Deterministic, with no
 * LLM: a mention that matches nothing gives no Share Class, never a guess.
 */
public final class ShareClassCatalog {

    private final List<ShareClass> shareClasses;

    /**
     * Throws {@link FundDataUnavailable} when the catalog cannot be read or is empty: an empty catalog would turn every
     * check into "fund not covered" (contracts/share-classes.md).
     */
    public ShareClassCatalog(ShareClasses source) {
        this.shareClasses = List.copyOf(source.all());
        if (shareClasses.isEmpty()) {
            throw new FundDataUnavailable("The Share Class catalog is empty", null);
        }
    }

    /** Every Share Class of the catalog, in the order the port gave them. */
    public List<ShareClass> all() {
        return shareClasses;
    }

    /**
     * What a mention or ISIN names: the Share Classes of one fund, in catalog order (a fund named without a class
     * gives all its classes), the candidate funds when it matches several, or nothing. The reverse-lookup search lists
     * every match of the same matcher instead ({@link ShareClassMatcher#match}).
     */
    public Resolution resolve(String mentionOrIsin) {
        Map<String, List<ShareClass>> byFund = new LinkedHashMap<>();
        for (ShareClass shareClass : ShareClassMatcher.match(mentionOrIsin, shareClasses)) {
            byFund.computeIfAbsent(ShareClassMatcher.fundKey(shareClass), key -> new ArrayList<>()).add(shareClass);
        }
        return switch (byFund.size()) {
            case 0 -> new NotFound();
            case 1 -> new Resolved(byFund.values().iterator().next());
            default -> new Ambiguous(byFund.values().stream()
                    .map(classes -> new CandidateFund(classes.getFirst().fundName(), classes))
                    .toList());
        };
    }
}
