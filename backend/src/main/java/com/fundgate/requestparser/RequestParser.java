package com.fundgate.requestparser;

import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.privacygate.GatedEmail;
import java.util.List;

/** Turns a gated email into Subscription Requests: one per portfolio and Share Class it names. */
public interface RequestParser {

    List<SubscriptionRequest> parse(GatedEmail email);
}
