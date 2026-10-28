package com.fundgate.ruleengine.stub;

import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.Justification;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.Verdict;
import com.fundgate.ruleengine.RuleEngine;
import java.util.List;

/** Walking skeleton (#57): no rule is checked, so every check needs review. */
public class StubRuleEngine implements RuleEngine {

    @Override
    public EligibilityResult check(SubscriptionRequest request, ClientProfile profile) {
        return new EligibilityResult(Verdict.NEEDS_REVIEW, new Justification(List.of(), List.of("Rule Engine not built yet")));
    }
}
