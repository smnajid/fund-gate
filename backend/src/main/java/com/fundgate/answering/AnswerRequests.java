package com.fundgate.answering;

import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioNotFound;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.privacygate.GateRefusal;
import com.fundgate.privacygate.GatedEmail;
import com.fundgate.privacygate.PrivacyGate;
import com.fundgate.replydrafter.DraftedReply;
import com.fundgate.replydrafter.ReplyDrafter;
import com.fundgate.replydrafter.ReplyFacts;
import com.fundgate.replydrafter.ReplyTemplate;
import com.fundgate.requestparser.ParsedEmail;
import com.fundgate.requestparser.RequestParser;
import com.fundgate.ruleengine.RuleEngine;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
        List<Checked> checked = new ArrayList<>();
        for (RequestByHand byHand : requests) {
            PortfolioId id = clientProfiles.portfolioId(byHand.portfolioRef())
                    .orElseThrow(() -> new PortfolioNotFound(byHand.portfolioRef()));
            refs.put(id, byHand.portfolioRef());
            checked.add(check(new SubscriptionRequest(id, byHand.shareClass(), byHand.amount(), byHand.clientInitiated()),
                    byHand.portfolioRef()));
        }
        DraftedReply reply = new DraftedReply(
                ReplyTemplate.write(facts(checked), id -> refs.get(id).value()), false, List.of());
        return answered(List.of(), refs, checked, reply, List.of());
    }

    private Answer answer(GatedEmail gated) {
        ParsedEmail parsed = requestParser.parse(gated.pseudonymised());
        // A Portfolio Id the Gate did not swap in names no portfolio of this email: fail closed, check nothing
        if (!parsed.requests().stream().allMatch(request -> gated.swaps().containsKey(request.portfolioId()))) {
            return new Answer.Refused("The request names a portfolio the email does not", List.of());
        }
        List<Checked> checked = parsed.requests().stream()
                .map(request -> check(request, gated.swaps().get(request.portfolioId())))
                .toList();
        DraftedReply drafted = replyDrafter.draft(gated.pseudonymised(), facts(checked));
        List<Answer.Swap> swaps = gated.swaps().entrySet().stream()
                .map(swap -> new Answer.Swap(swap.getValue(), swap.getKey()))
                .toList();
        DraftedReply reply = new DraftedReply(gated.restore(drafted.text()), drafted.draftedByAi(), drafted.llmCalls());
        return answered(swaps, gated.swaps(), checked, reply,
                Stream.concat(parsed.llmCalls().stream(), drafted.llmCalls().stream()).toList());
    }

    private record Checked(SubscriptionRequest request, EligibilityResult result) {
    }

    private Checked check(SubscriptionRequest request, PortfolioReference portfolioRef) {
        return new Checked(request, ruleEngine.check(request, clientProfiles.clientProfile(portfolioRef)));
    }

    private static List<ReplyFacts> facts(List<Checked> checked) {
        return checked.stream().map(check -> ReplyFacts.of(check.request(), check.result())).toList();
    }

    private Answer.Answered answered(List<Answer.Swap> swaps, Map<PortfolioId, PortfolioReference> refs,
            List<Checked> checked, DraftedReply reply, List<CheckRecord.LlmCall> llmCalls) {
        return new Answer.Answered(swaps, checked.stream()
                .map(check -> record(refs.get(check.request().portfolioId()), check, llmCalls))
                .toList(), reply);
    }

    /** Every LLM call the email needed goes in the Check Record of each of its checks. */
    private Answer.Check record(PortfolioReference portfolioRef, Checked check, List<CheckRecord.LlmCall> llmCalls) {
        Answer.Check answered = new Answer.Check(UUID.randomUUID(), portfolioRef, check.request(), check.result());
        checkRecords.write(new CheckRecord(answered.checkId(), clock.instant(), DEALER, portfolioRef,
                check.request(), Optional.empty(), NO_VERSION, NO_VERSION, List.of(), check.result().verdict(),
                check.result().justification(), llmCalls));
        return answered;
    }
}
