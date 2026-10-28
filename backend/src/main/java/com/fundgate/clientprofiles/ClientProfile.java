package com.fundgate.clientprofiles;

import com.fundgate.bankdata.BankCode;
import com.fundgate.bankdata.Country;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything an Eligibility Check reads about one portfolio (contracts/client-profile.md).
 * Every fact is optional: absent means unknown, and adapters never fill a default (ADR 0008).
 */
public record ClientProfile(
        PortfolioReference portfolioRef,
        PortfolioId portfolioId,
        String legalEntity,
        Instant retrievedAt,
        Optional<Client> client,
        Optional<Classification> classification,
        Optional<Portfolio> portfolio) {

    /** The required fields: a fixture or bank answer without them is refused, never read as null. */
    public ClientProfile {
        Objects.requireNonNull(portfolioRef, "portfolioRef");
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(legalEntity, "legalEntity");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
        if (!legalEntity.equals(portfolioRef.legalEntity())) {
            throw new IllegalArgumentException("legalEntity " + legalEntity + " is not the Legal Entity of " + portfolioRef);
        }
    }

    /** Facts about the person. */
    public record Client(
            Optional<Country> residenceCountry,
            Optional<List<Country>> nationalities,
            Optional<List<Country>> taxResidences,
            Optional<Country> birthCountry,
            Optional<UsPerson> usPerson,
            Optional<List<KnowledgeAndExperience>> knowledgeAndExperience) {
    }

    public record UsPerson(Optional<FatcaStatus> fatcaStatus, Optional<Boolean> usPermanentResident) {
    }

    public enum FatcaStatus { US_PERSON, NON_US_PERSON }

    /** An entry missing its product type or level is ignored by the Target Market, as the contract says. */
    public record KnowledgeAndExperience(Optional<BankCode> productType, Optional<BankCode> level) {
    }

    /** The Investor Classification the portfolio's Legal Entity gives the client. */
    public record Classification(
            Optional<Regime> regime,
            Optional<Category> category,
            Optional<LocalDate> classifiedOn,
            /** Empty: none on file. Absent: unknown. */
            Optional<List<Declaration>> declarations) {

        public enum Regime { FINSA, MIFID }

        /** INSTITUTIONAL covers eligible counterparties (MiFID) and institutional clients (FinSA). */
        public enum Category { RETAIL, PROFESSIONAL, INSTITUTIONAL }

        public record Declaration(DeclarationType type, Optional<LocalDate> date, Optional<String> label) {

            public Declaration {
                Objects.requireNonNull(type, "declaration type: an unmapped code is OTHER, never absent");
            }
        }

        public enum DeclarationType {
            FINSA_HNWI_OPT_OUT,
            FINSA_OPT_IN_RETAIL,
            MIFID_PROFESSIONAL_ON_REQUEST,
            LU_WELL_INFORMED_CONFIRMATION,
            OTHER
        }
    }

    /** Facts about the dossier. */
    public record Portfolio(
            Optional<String> referenceCurrency,
            Optional<Mandate> mandate,
            Optional<InvestmentProfile> investmentProfile) {
    }

    /** {@code type} is empty when the bank's code matches no value: unknown, never a guess. */
    public record Mandate(Optional<MandateType> type, Optional<LocalDate> startDate, Optional<Manager> manager) {
    }

    public enum MandateType { ADVISORY, DISCRETIONARY, EXECUTION_ONLY }

    public record Manager(Optional<ManagerKind> kind, Optional<String> name) {
    }

    public enum ManagerKind { BANK, EXTERNAL_SUPERVISED, EXTERNAL_OTHER }

    public record InvestmentProfile(
            Optional<BankCode> riskProfile,
            Optional<BankCode> abilityToBearLosses,
            Optional<Integer> horizonYears,
            Optional<LocalDate> assessedOn) {
    }
}
