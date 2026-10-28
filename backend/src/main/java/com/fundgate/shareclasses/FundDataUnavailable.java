package com.fundgate.shareclasses;

/** The bank's fund data could not be read. No Verdict is given on partial data. */
public class FundDataUnavailable extends RuntimeException {

    public FundDataUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
