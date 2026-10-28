package com.fundgate.ruleengine;

import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.SubscriptionRequest;

/** Runs one Eligibility Check with fixed rules and no LLM (ADR 0002). */
public interface RuleEngine {

    EligibilityResult check(SubscriptionRequest request);
}
