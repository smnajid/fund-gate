package com.fundgate.validation.web;

import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.rulecard.Condition;
import com.fundgate.ruleengine.RuleCatalog;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A rule's condition as a card's JSON writes it, e.g. {@code {"kind": "investor_category", "allowed": [...]}}: what
 * the screen shows and what a correction sends back. Read as a card is, so a field the kind does not have is refused.
 */
final class ConditionJson {

    private static final TypeReference<Map<String, Object>> FIELDS = new TypeReference<>() {
    };

    private final JsonMapper json = FixtureJson.knowing(RuleCatalog.conditionTypes());

    Map<String, Object> write(Condition condition) {
        return json.convertValue(condition, FIELDS);
    }

    /** Throws {@link IllegalArgumentException} for a condition outside the Rule Card schema. */
    Condition read(Map<String, Object> fields) {
        if (fields == null) {
            throw new IllegalArgumentException("A correction sends the corrected condition");
        }
        try {
            return json.convertValue(fields, Condition.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("The corrected condition is outside the Rule Card schema (kind "
                    + fields.getOrDefault("kind", "missing") + ")");
        }
    }
}
