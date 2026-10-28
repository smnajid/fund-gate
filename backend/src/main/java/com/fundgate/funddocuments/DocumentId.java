package com.fundgate.funddocuments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/** FMD's document id; in the folder adapter, the manifest's own id. */
public record DocumentId(@JsonValue String value) {

    @JsonCreator
    public DocumentId {
        Objects.requireNonNull(value, "document id");
    }

    @Override
    public String toString() {
        return value;
    }
}
