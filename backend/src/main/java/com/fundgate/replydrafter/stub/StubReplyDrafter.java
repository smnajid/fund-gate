package com.fundgate.replydrafter.stub;

import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.privacygate.PseudonymisedEmail;
import com.fundgate.replydrafter.DraftedReply;
import com.fundgate.replydrafter.ReplyDrafter;
import com.fundgate.replydrafter.ReplyFacts;
import com.fundgate.replydrafter.ReplyTemplate;
import java.util.List;

/** Walking skeleton (#57): the template, no LLM. */
public class StubReplyDrafter implements ReplyDrafter {

    @Override
    public DraftedReply draft(PseudonymisedEmail email, List<ReplyFacts> checks) {
        return new DraftedReply(ReplyTemplate.write(checks, PortfolioId::toString), false, List.of());
    }
}
