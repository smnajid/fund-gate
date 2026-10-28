package com.fundgate.answering.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/** The Requester's email as the Fund Dealer pasted it. It may name a client, so it is never printed. */
public record EmailToAnswer(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {

    public EmailToAnswer {
        Objects.requireNonNull(email, "email");
    }

    @Override
    public String toString() {
        return "EmailToAnswer[" + email.length() + " characters]";
    }
}
