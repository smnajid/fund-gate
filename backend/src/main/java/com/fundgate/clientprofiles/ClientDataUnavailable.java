package com.fundgate.clientprofiles;

/** The bank's client data could not be read. No Verdict is given on partial data. */
public class ClientDataUnavailable extends RuntimeException {

    public ClientDataUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
