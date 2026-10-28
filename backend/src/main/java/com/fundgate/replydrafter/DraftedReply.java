package com.fundgate.replydrafter;

import java.util.Objects;

/** The reply to a Requester, and whether an LLM drafted it, so the screen labels it AI-generated; else it is a template. */
public record DraftedReply(String text, boolean draftedByAi) {

    public DraftedReply {
        Objects.requireNonNull(text, "text");
    }
}
