package com.fundgate.funddocuments;

/** A document's PDF could not be read. */
public class DocumentUnavailable extends RuntimeException {

    public DocumentUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
