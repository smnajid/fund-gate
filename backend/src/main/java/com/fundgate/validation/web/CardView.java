package com.fundgate.validation.web;

import com.fundgate.citation.Citation;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.shareclasses.Isin;
import com.fundgate.validation.CardReview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the validation screen shows of one card version; the shape the Angular client is generated from. It carries
 * the signals the code checks and no model confidence (#34). validator and validatedAt are null on a Draft.
 */
public record CardView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fundName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> shareClasses,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RuleCard.Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant draftedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String draftedBy,
        String validator,
        Instant validatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean everyQuoteFoundOnPage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean readyToValidate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean inForce,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RuleView> rules,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ContradictionView> contradictions) {

    /** {@code conditions} writes a condition as a card's JSON does, its kind named; {@code types} names each document. */
    static CardView of(CardReview review, ConditionJson conditions, Map<DocumentId, FundDocument.Type> types) {
        RuleCard card = review.card();
        return new CardView(card.fundName(), card.shareClasses().stream().map(Isin::value).toList(), card.version(),
                card.status(), card.drafting().draftedAt(), card.drafting().model(),
                card.validation().map(RuleCard.Validation::validator).orElse(null),
                card.validation().map(RuleCard.Validation::validatedAt).orElse(null),
                review.everyQuoteFoundOnPage(), review.readyToValidate(), review.inForce(),
                review.rules().stream().map(rule -> RuleView.of(rule, conditions, types)).toList(),
                review.contradictions().stream().map(ContradictionView::of).toList());
    }

    /** One rule; shareClass is null for a rule on every class. by and at name its last step. */
    public record RuleView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int index,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Rule.Scope scope,
            String shareClass,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> condition,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CardReview.Status status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean quoteFoundOnPage,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean noSupportingQuote,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean unsettledContradiction,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CitationView> citations,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String by,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at) {

        static RuleView of(CardReview.RuleReview review, ConditionJson conditions,
                Map<DocumentId, FundDocument.Type> types) {
            Rule rule = review.rule();
            Rule.Revision last = rule.history().getLast();
            return new RuleView(review.index(), review.kind(), rule.scope(),
                    rule.shareClass().map(Isin::value).orElse(null), conditions.write(rule.condition()),
                    rule.summary(), review.status(), review.signals().quoteFoundOnPage(),
                    review.signals().noSupportingQuote(), review.signals().unsettledContradiction(),
                    rule.citations().stream().map(citation -> CitationView.of(citation, types)).toList(),
                    last.by(), last.at());
        }
    }

    /** A passage the rule cites; documentType is null for a document the bank does not list. */
    public record CitationView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String document,
            FundDocument.Type documentType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String quote,
            /** Null until the quote check has run. */
            Boolean quoteFoundOnPage) {

        static CitationView of(Citation.Document citation, Map<DocumentId, FundDocument.Type> types) {
            return new CitationView(citation.document().value(), types.get(citation.document()), citation.page(),
                    citation.quote(), citation.quoteFoundOnPage().orElse(null));
        }
    }

    /** Rules that read one kind for one scope differently; shareClass is null for every class. */
    public record ContradictionView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
            String shareClass,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Integer> rules,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean settled) {

        static ContradictionView of(CardReview.Contradiction contradiction) {
            return new ContradictionView(contradiction.kind(),
                    contradiction.shareClass().map(Isin::value).orElse(null), contradiction.rules(),
                    contradiction.settled());
        }
    }
}
