package com.fundgate.eligibility;

/** What an Eligibility Check gives: the Verdict and its Justification. */
public record EligibilityResult(Verdict verdict, Justification justification) {
}
