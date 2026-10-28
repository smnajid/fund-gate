package com.fundgate.answering;

import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioNotFound;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.privacygate.GateRefusal;
import com.fundgate.privacygate.GatedEmail;
import com.fundgate.privacygate.PrivacyGate;
import com.fundgate.replydrafter.CheckedRequest;
import com.fundgate.replydrafter.DraftedReply;
import com.fundgate.replydrafter.ReplyDrafter;
import com.fundgate.replydrafter.ReplyTemplate;
import com.fundgate.requestparser.RequestParser;
import com.fundgate.ruleengine.RuleEngine;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In port: answers a Requester's email, module by module (architecture.md, Data flow of one request). */
public class AnswerRequests {

    // Until the dealer signs in, and the Rule Engine and Scale Mapping replace their stubs
    private static final String DEALER = "not signed in";
    private static final String NO_VERSION = "stub";

    private final ClientProfiles clientProfiles;
    private final PrivacyGate privacyGate;
    private final RequestParser requestParser;
    private final RuleEngine ruleEngine;
    private final ReplyDrafter replyDrafter;
    private final CheckRecords checkRecords;
    private final Clock clock;

    public AnswerRequests(ClientProfiles clientProfiles, PrivacyGate privacyGate, RequestParser requestParser,
            RuleEngine ruleEngine, ReplyDrafter replyDrafter, CheckRecords checkRecords, Clock clock) {
        this.clientProfiles = clientProfiles;
        this.privacyGate = privacyGate;
        this.requestParser = requestParser;
        this.ruleEngine = ruleEngine;
        this.replyDrafter = replyDrafter;
        this.checkRecords = checkRecords;
        this.clock = clock;
    }

    public Answer answer(String email) {
        return switch (privacyGate.gate(email)) {
            case GateRefusal refusal -> new Answer.Refused(refusal.reason(), refusal.found());
            case GatedEmail gated -> answer(gated);
        };
    }

    /** No LLM at all: the requests are checked and the reply is the template. Throws {@link PortfolioNotFound}. */
    public Answer.Answered answerByHand(List<RequestByHand> requests) {
        Map<PortfolioId, PortfolioReference> refs = new LinkedHashMap<>();
        List<CheckedRequest> checked = new ArrayList<>();
        for (RequestByHand byHand : requests) {
            PortfolioId id = clientProfiles.portfolioId(byHand.portfolioRef())
                    .orElseThrow(() -> new PortfolioNotFound(byHand.portfolioRef()));
            refs.put(id, byHand.portfolioRef());
            checked.add(check(new SubscriptionRequest(id, byHand.shareClass(), byHand.amount(), byHand.clientInitiated())));
        }
        DraftedReply reply = new DraftedReply(ReplyTemplate.write(checked, id -> refs.get(id).value()), false);
        return answered(List.of(), refs, checked, reply);
    }

    private Answer.Answered answer(GatedEmail gated) {
        List<CheckedRequest> checked = requestParser.parse(gated).stream().map(this::check).toList();
        DraftedReply drafted = replyDrafter.draft(gated, checked);
        List<Answer.Swap> swaps = gated.swaps().entrySet().stream()
                .map(swap -> new Answer.Swap(swap.getValue(), swap.getKey()))
                .toList();
        return answered(swaps, gated.swaps(), checked,
                new DraftedReply(gated.restore(drafted.text()), drafted.draftedByAi()));
    }

    private Answer.Answered answered(List<Answer.Swap> swaps, Map<PortfolioId, PortfolioReference> refs,
            List<CheckedRequest> checked, DraftedReply reply) {
        return new Answer.Answered(swaps,
                checked.stream().map(check -> record(refs.get(check.request().portfolioId()), check)).toList(), reply);
    }

    private CheckedRequest check(SubscriptionRequest request) {
        return new CheckedRequest(request, ruleEngine.check(request));
    }

    private Answer.Check record(PortfolioReference portfolioRef, CheckedRequest check) {
        Answer.Check answered = new Answer.Check(UUID.randomUUID(), portfolioRef, check.request(), check.result());
        checkRecords.write(new CheckRecord(answered.checkId(), clock.instant(), DEALER, portfolioRef,
                check.request(), Optional.empty(), NO_VERSION, NO_VERSION, List.of(), check.result().verdict(),
                check.result().justification(), List.of()));
        return answered;
    }
}
