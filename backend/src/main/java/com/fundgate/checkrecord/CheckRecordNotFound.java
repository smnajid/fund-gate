package com.fundgate.checkrecord;

import java.util.UUID;

/** No Check Record has this checkId. */
public class CheckRecordNotFound extends RuntimeException {

    public CheckRecordNotFound(UUID checkId) {
        super("No Check Record " + checkId);
    }
}
