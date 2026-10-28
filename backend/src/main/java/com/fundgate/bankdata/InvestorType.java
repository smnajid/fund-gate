package com.fundgate.bankdata;

/**
 * Who a Share Class may be offered to, a legal category (ADR 0008). ALL covers everyone. QUALIFIED (CH) and
 * WELL_INFORMED (LU) are derived by the engine, never stored on a Client Profile.
 */
public enum InvestorType { ALL, RETAIL, PROFESSIONAL, INSTITUTIONAL, QUALIFIED, WELL_INFORMED }
