package com.fundgate.replydrafter;

import com.fundgate.privacygate.PseudonymisedEmail;
import java.util.List;

/**
 * Drafts the reply to a pseudonymised email from its checked requests. It sees Portfolio Ids only; the caller puts the
 * Portfolio References back locally, after any LLM call (ADR 0004).
 */
public interface ReplyDrafter {

    DraftedReply draft(PseudonymisedEmail email, List<ReplyFacts> checks);
}
