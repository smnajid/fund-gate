package com.fundgate.requestparser;

import com.fundgate.privacygate.GatedEmail;

/** Turns a gated email into Subscription Requests: one per portfolio and Share Class it names. */
public interface RequestParser {

    ParsedEmail parse(GatedEmail email);
}
