package com.fundgate.checkrecord;

import java.util.List;
import java.util.UUID;

/** Out port: the Fund Dealer's corrections, appended beside the Check Records and never changed either. */
public interface DealerCorrections {

    void append(DealerCorrection correction);

    /** The corrections of one check, oldest first. */
    List<DealerCorrection> of(UUID checkId);
}
