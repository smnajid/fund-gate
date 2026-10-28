package com.fundgate.requestparser;

import com.fundgate.privacygate.PseudonymisedEmail;

/** Turns a pseudonymised email into Subscription Requests: one per portfolio and Share Class it names. */
public interface RequestParser {

    ParsedEmail parse(PseudonymisedEmail email);
}
