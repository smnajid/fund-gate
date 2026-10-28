package com.fundgate.bankdata;

/** A value on the bank's own scale, passed through unchanged; only the Scale Mapping translates it (ADR 0008). */
public record BankCode(String code, String label) {
}
