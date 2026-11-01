package com.fundgate.checkrecord;

import java.util.List;
import java.util.Objects;

/** A Check Record and the Fund Dealer's corrections beside it, oldest first: what "View Check Record" shows. */
public record AuditedCheck(CheckRecord record, List<DealerCorrection> corrections) {

    public AuditedCheck {
        Objects.requireNonNull(record, "record");
        corrections = List.copyOf(corrections);
    }
}
