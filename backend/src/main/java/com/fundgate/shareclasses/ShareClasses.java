package com.fundgate.shareclasses;

import java.util.List;

/** Out port: the Share Class catalog (contracts/share-classes.md). */
public interface ShareClasses {

    /** Every Share Class the bank distributes. Throws {@link FundDataUnavailable}. */
    List<ShareClass> all();
}
