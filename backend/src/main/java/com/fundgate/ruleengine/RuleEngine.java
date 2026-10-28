package com.fundgate.ruleengine;

import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.SubscriptionRequest;

/** Runs one Eligibility Check with fixed rules and no LLM (ADR 0002). */
public interface RuleEngine {

    /** The profile is the requested portfolio's, read by the caller, which holds its Portfolio Reference. */
    EligibilityResult check(SubscriptionRequest request, ClientProfile profile);
}
