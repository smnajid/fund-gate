package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.ShareClasses;
import java.util.List;

/**
 * The Share Classes the bank distributes, read once and kept in memory, and the matcher that turns a fund or class
 * mention, or an ISIN, into the Share Classes it names (architecture.md, Share Class catalog). Deterministic, with no
 * LLM: a mention that matches nothing gives no Share Class, never a guess.
 */
public final class ShareClassCatalog {

    private final List<ShareClass> shareClasses;

    /**
     * Throws {@link com.fundgate.bankdata.FundDataUnavailable} when the catalog cannot be read: an empty catalog would
     * turn every check into "fund not covered" (contracts/share-classes.md).
     */
    public ShareClassCatalog(ShareClasses source) {
        this.shareClasses = List.copyOf(source.all());
    }

    /** Every Share Class of the catalog, in the order the port gave them. */
    public List<ShareClass> all() {
        return shareClasses;
    }

    /**
     * The Share Classes a mention or ISIN names, in catalog order; a fund named without a class gives all its classes.
     * The reverse-lookup search uses the same matching ({@link ShareClassMatcher}).
     */
    public List<ShareClass> find(String mentionOrIsin) {
        return ShareClassMatcher.match(mentionOrIsin, shareClasses);
    }
}
