package com.fundgate.specs.ruleengine;

import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.shareclasses.Isin;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The fixture cards, plus the versions a scenario's Rule Card Validator saved; forgotten before each scenario. */
public class EditableRuleCards implements RuleCards {

    private final RuleCards fixtures;
    private final Map<Isin, RuleCard> saved = new HashMap<>();

    public EditableRuleCards(RuleCards fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public Optional<RuleCard> forShareClass(Isin shareClass) {
        return Optional.ofNullable(saved.get(shareClass)).or(() -> fixtures.forShareClass(shareClass));
    }

    /** A new version supersedes the card for every class it covers. */
    public void save(RuleCard card) {
        card.shareClasses().forEach(shareClass -> saved.put(shareClass, card));
    }

    public void reset() {
        saved.clear();
    }
}
