package com.fundgate.privacygate;

/** What the Privacy Gate makes of an email: let through with Portfolio Ids, or refused, and then no LLM call is made. */
public sealed interface GateResult permits GatedEmail, GateRefusal {
}
