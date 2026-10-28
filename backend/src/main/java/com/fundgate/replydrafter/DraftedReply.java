package com.fundgate.replydrafter;

import com.fundgate.checkrecord.CheckRecord;
import java.util.List;
import java.util.Objects;

/**
 * The reply to a Requester, whether an LLM drafted it, so the screen labels it AI-generated, else it is a template,
 * and the LLM calls made to draft it, for the Check Record.
 */
public record DraftedReply(String text, boolean draftedByAi, List<CheckRecord.LlmCall> llmCalls) {

    public DraftedReply {
        Objects.requireNonNull(text, "text");
        llmCalls = List.copyOf(llmCalls);
    }
}
