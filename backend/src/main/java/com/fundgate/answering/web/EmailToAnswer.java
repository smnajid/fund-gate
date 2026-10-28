package com.fundgate.answering.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** The Requester's email as the Fund Dealer pasted it. */
public record EmailToAnswer(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {
}
