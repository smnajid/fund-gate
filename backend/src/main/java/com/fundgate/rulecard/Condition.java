package com.fundgate.rulecard;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The typed parameters of one rule. The LLM only picks the kind and fills these in; one evaluator per kind decides.
 * A card's JSON names the kind as the pipeline's schema does, e.g. {@code "kind": "investor_category"}. Each kind's
 * condition lives in its plug-in, with its evaluator and its prompt line (issue #79); the reader of the cards
 * registers the names (com.fundgate.ruleengine.RuleCatalog).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
public interface Condition {

    RuleKind kind();
}
