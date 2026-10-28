package com.fundgate.requestparser;

import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.eligibility.SubscriptionRequest;
import java.util.List;

/** The Subscription Requests read from a pseudonymised email, and the LLM calls made to read them, for the Check Record. */
public record ParsedEmail(List<SubscriptionRequest> requests, List<CheckRecord.LlmCall> llmCalls) {

    public ParsedEmail {
        requests = List.copyOf(requests);
        llmCalls = List.copyOf(llmCalls);
    }
}
