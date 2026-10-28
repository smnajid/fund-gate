package com.fundgate.checkrecord;

import java.util.Optional;
import java.util.UUID;

/** Out port: where Check Records are written once and read back for an audit (architecture.md, Audit). */
public interface CheckRecords {

    void write(CheckRecord record);

    Optional<CheckRecord> find(UUID checkId);
}
