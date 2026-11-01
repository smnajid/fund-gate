package com.fundgate.checkrecord.memory;

import com.fundgate.checkrecord.DealerCorrection;
import com.fundgate.checkrecord.DealerCorrections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Kept in memory like the Check Records, so a restart forgets every correction. Append only. */
public class InMemoryDealerCorrections implements DealerCorrections {

    private final List<DealerCorrection> corrections = new CopyOnWriteArrayList<>();

    @Override
    public void append(DealerCorrection correction) {
        corrections.add(correction);
    }

    @Override
    public List<DealerCorrection> of(UUID checkId) {
        return corrections.stream().filter(correction -> correction.checkId().equals(checkId)).toList();
    }
}
