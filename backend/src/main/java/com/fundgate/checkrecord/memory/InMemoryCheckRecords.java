package com.fundgate.checkrecord.memory;

import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Walking skeleton (#57): kept in memory, so a restart forgets every check. */
public class InMemoryCheckRecords implements CheckRecords {

    private final Map<UUID, CheckRecord> records = new ConcurrentHashMap<>();

    @Override
    public void write(CheckRecord record) {
        if (records.putIfAbsent(record.checkId(), record) != null) {
            throw new IllegalStateException("A Check Record is written once: " + record.checkId());
        }
    }

    @Override
    public Optional<CheckRecord> find(UUID checkId) {
        return Optional.ofNullable(records.get(checkId));
    }
}
