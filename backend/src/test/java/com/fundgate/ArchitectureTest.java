package com.fundgate;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.base.DescribedPredicate.describe;

import com.fundgate.privacygate.GatedEmail;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The boundaries the ADRs draw, checked on every build rather than left to review: the Rule Engine never reaches an
 * LLM, only the Privacy Gate lets an email through, and Spring stays in the wiring and the web layer.
 */
@AnalyzeClasses(packages = "com.fundgate", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** ADR 0002: an Eligibility Check involves no LLM, so a Verdict stays reproducible. */
    @ArchTest
    static final ArchRule the_rule_engine_never_depends_on_the_llm = noClasses()
            .that().resideInAPackage("com.fundgate.ruleengine..")
            .should().dependOnClassesThat().resideInAPackage("com.fundgate.llm..");

    /** ADR 0004: a GatedEmail is Privacy Gate output; built anywhere else, it would carry a raw email past the Gate. */
    @ArchTest
    static final ArchRule only_the_privacy_gate_builds_a_gated_email = noClasses()
            .that().resideOutsideOfPackage("com.fundgate.privacygate..")
            // Any constructor of it, so a changed signature cannot leave the rule matching nothing
            .should().callConstructorWhere(target(owner(describe("GatedEmail", owner -> owner.isEquivalentTo(GatedEmail.class)))));

    /** The modules are plain Java, so a later ticket swaps a stub by changing one bean, and their tests need no Spring. */
    @ArchTest
    static final ArchRule spring_stays_in_the_wiring_and_the_web_layer = noClasses()
            .that().resideOutsideOfPackages("com.fundgate", "com.fundgate..web..")
            .and().haveSimpleNameNotEndingWith("Configuration")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");
}
