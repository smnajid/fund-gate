package com.fundgate.replydrafter;

import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.SubscriptionRequest;

/** One Subscription Request with the result of its Eligibility Check, as the Reply Drafter sees it: no Portfolio Reference. */
public record CheckedRequest(SubscriptionRequest request, EligibilityResult result) {
}
