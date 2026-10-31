package com.fundgate;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.base.DescribedPredicate.describe;

import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.privacygate.GatedEmail;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

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

    /**
     * ADR 0004: the modules that will call the LLM get the gated text and Portfolio Ids only. The Gate's swap table
     * holds the Portfolio References, so it stays with the Gate and the in port that swaps them back.
     */
    @ArchTest
    static final ArchRule the_llm_modules_never_see_a_portfolio_reference = noClasses()
            .that().resideInAnyPackage("com.fundgate.requestparser..", "com.fundgate.replydrafter..")
            .should().dependOnClassesThat().belongToAnyOf(PortfolioReference.class, GatedEmail.class);

    /** The modules are plain Java, so a later ticket swaps a stub by changing one bean, and their tests need no Spring. */
    @ArchTest
    static final ArchRule spring_stays_in_the_wiring_and_the_web_layer = noClasses()
            .that().resideOutsideOfPackages("com.fundgate", "com.fundgate..web..")
            .and().haveSimpleNameNotEndingWith("Configuration")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /** Issue #79: the Rule Card knows no kind of rule; the engine's catalog brings them, so a kind is added there only. */
    @ArchTest
    static final ArchRule the_rule_card_never_depends_on_the_rule_engine = noClasses()
            .that().resideInAPackage("com.fundgate.rulecard..")
            .should().dependOnClassesThat().resideInAPackage("com.fundgate.ruleengine..");

    /** Issue #79: a kind of rule is one plug-in, so its condition is used by its plug-in alone. */
    @ArchTest
    static final ArchRule a_kind_s_condition_is_used_only_by_its_plug_in = classes()
            .that().implement(Condition.class)
            .should(new ArchCondition<>("be used only by the plug-in that holds it") {
                @Override
                public void check(JavaClass condition, ConditionEvents events) {
                    JavaClass plugin = outermost(condition);
                    condition.getDirectDependenciesToSelf().stream()
                            .filter(dependency -> !outermost(dependency.getOriginClass()).equals(plugin))
                            .forEach(dependency -> events.add(SimpleConditionEvent.violated(dependency,
                                    dependency.getDescription())));
                }
            });

    /** Issue #79: outside the plug-ins, no code names a kind on the card; Fund Registration is not on the card. */
    @ArchTest
    static final ArchRule only_the_plug_ins_name_a_kind_on_the_card = noClasses()
            .that(describe("are outside the plug-ins and RuleKind", (JavaClass owner) ->
                    !outermost(owner).isAssignableTo("com.fundgate.ruleengine.RuleKindPlugin")
                            && !owner.isEquivalentTo(RuleKind.class)))
            .should().accessFieldWhere(describe("a kind on the card", (JavaFieldAccess access) ->
                    access.getTargetOwner().isEquivalentTo(RuleKind.class)
                            && !access.getName().equals(RuleKind.FUND_REGISTRATION.name())));

    private static JavaClass outermost(JavaClass type) {
        return type.getEnclosingClass().map(ArchitectureTest::outermost).orElse(type);
    }
}
