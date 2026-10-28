package com.fundgate.requestparser.stub;

import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.privacygate.PseudonymisedEmail;
import com.fundgate.requestparser.ParsedEmail;
import com.fundgate.requestparser.RequestParser;
import com.fundgate.shareclasses.Isin;
import java.util.List;
import java.util.Optional;

/** Walking skeleton (#57): one request per Portfolio Id, all on Nordea 1 Global Climate BI-EUR, no amount, no LLM. */
public class StubRequestParser implements RequestParser {

    private static final Isin GLOBAL_CLIMATE_BI_EUR = new Isin("LU0348927095");

    @Override
    public ParsedEmail parse(PseudonymisedEmail email) {
        return new ParsedEmail(email.portfolioIds().stream()
                .map(id -> new SubscriptionRequest(id, GLOBAL_CLIMATE_BI_EUR, Optional.empty(), ClientInitiated.UNKNOWN))
                .toList(), List.of());
    }
}
