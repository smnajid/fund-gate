package com.fundgate.privacygate;

/**
 * Swaps every Portfolio Reference of a Requester's email for its Portfolio Id before any LLM call, and fails closed
 * (ADR 0004).
 */
public interface PrivacyGate {

    GateResult gate(String email);
}
