package com.fundgate.clientprofiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    /** Facts about the person. */
    public record Client(
            Optional<String> residenceCountry,
            Optional<List<String>> nationalities,
            Optional<List<String>> taxResidences,
            Optional<String> birthCountry,
            Optional<UsPerson> usPerson,
            Optional<List<KnowledgeAndExperience>> knowledgeAndExperience) {
    }

    public record UsPerson(Optional<FatcaStatus> fatcaStatus, Optional<Boolean> usPermanentResident) {
    }

    public enum FatcaStatus { US_PERSON, NON_US_PERSON }

    public record KnowledgeAndExperience(BankCode productType, BankCode level) {
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

    public record Mandate(MandateType type, Optional<LocalDate> startDate, Optional<Manager> manager) {
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
