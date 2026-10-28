package com.fundgate.shareclasses;

import com.fundgate.bankdata.BankCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One Share Class the bank distributes (contracts/share-classes.md). */
public record ShareClass(
        Isin isin,
        Optional<String> fundId,
        String fundName,
        Optional<String> umbrellaName,
        String shareClassName,
        String currency,
        Optional<String> domicile,
        Optional<FundType> fundType,
        /** The bank's product type, on the same scale as the Client Profile's knowledge and experience. */
        Optional<BankCode> productType,
        Optional<String> valor,
        Optional<String> internalCode,
        Instant retrievedAt) {

    public ShareClass {
        Objects.requireNonNull(isin, "isin");
        Objects.requireNonNull(fundName, "fundName");
        Objects.requireNonNull(shareClassName, "shareClassName");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
    }

    /** The fund's legal form, a legal category (ADR 0008). */
    public enum FundType { UCITS, SIF, RAIF, PART_II, CH_SECURITIES_FUND, CH_OTHER_FUND, CH_QUALIFIED_INVESTOR_FUND, OTHER }
}
