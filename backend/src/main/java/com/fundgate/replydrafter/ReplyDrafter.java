package com.fundgate.replydrafter;

import com.fundgate.privacygate.GatedEmail;
import java.util.List;

/**
 * Drafts the reply to a gated email from its checked requests. It sees Portfolio Ids only; the caller puts the
 * Portfolio References back locally, after any LLM call (ADR 0004).
 */
public interface ReplyDrafter {

    DraftedReply draft(GatedEmail email, List<CheckedRequest> checks);
}
