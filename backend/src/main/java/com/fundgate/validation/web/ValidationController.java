package com.fundgate.validation.web;

import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.shareclasses.Isin;
import com.fundgate.validation.CardReview;
import com.fundgate.validation.PageText;
import com.fundgate.validation.RuleCardValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Rule Card validation screen (screens.md): the cards to review, each rule's review step, and validating a card.
 * A card version is named by any of its Share Classes and its version number; a rule by its place on the card.
 */
@RestController
@Profile("!pipeline")
@RequestMapping(value = "/api/rule-cards", produces = MediaType.APPLICATION_JSON_VALUE)
public class ValidationController {

    private final RuleCardValidation validation;
    private final FundDocuments fundDocuments;
    private final PageText pageText;
    private final ConditionJson conditions = new ConditionJson();

    public ValidationController(RuleCardValidation validation, FundDocuments fundDocuments, PageText pageText) {
        this.validation = validation;
        this.fundDocuments = fundDocuments;
        this.pageText = pageText;
    }

    /** The latest version of each fund's card. */
    @GetMapping
    public List<CardView> cards() {
        return validation.cards().stream().map(this::view).toList();
    }

    /** Every version of the card of the class's fund, oldest first. */
    @GetMapping("/{shareClass}/versions")
    public List<CardView> versions(@PathVariable String shareClass) {
        return validation.versions(new Isin(shareClass)).stream().map(this::view).toList();
    }

    @GetMapping("/{shareClass}/versions/{version}")
    public CardView card(@PathVariable String shareClass, @PathVariable int version) {
        return view(validation.review(new Isin(shareClass), version));
    }

    @PostMapping("/{shareClass}/versions/{version}/rules/{rule}/accept")
    public CardView accept(@PathVariable String shareClass, @PathVariable int version, @PathVariable int rule,
            @RequestBody ReviewStep step) {
        validation.accept(new Isin(shareClass), version, rule, step.validator());
        return card(shareClass, version);
    }

    /** Also how an Unmodelled Restriction is marked Blocking or Noted (ADR 0003). */
    @PostMapping("/{shareClass}/versions/{version}/rules/{rule}/correct")
    public CardView correct(@PathVariable String shareClass, @PathVariable int version, @PathVariable int rule,
            @RequestBody Correction correction) {
        validation.correct(new Isin(shareClass), version, rule, conditions.read(correction.condition()),
                correction.validator());
        return card(shareClass, version);
    }

    @PostMapping("/{shareClass}/versions/{version}/rules/{rule}/reject")
    public CardView reject(@PathVariable String shareClass, @PathVariable int version, @PathVariable int rule,
            @RequestBody ReviewStep step) {
        validation.reject(new Isin(shareClass), version, rule, step.validator());
        return card(shareClass, version);
    }

    /** Settles a contradiction: this rule's document governs, its rivals are rejected. */
    @PostMapping("/{shareClass}/versions/{version}/rules/{rule}/follow")
    public CardView follow(@PathVariable String shareClass, @PathVariable int version, @PathVariable int rule,
            @RequestBody ReviewStep step) {
        validation.follow(new Isin(shareClass), version, rule, step.validator());
        return card(shareClass, version);
    }

    @PostMapping("/{shareClass}/versions/{version}/validate")
    public CardView validate(@PathVariable String shareClass, @PathVariable int version,
            @RequestBody ReviewStep step) {
        validation.validate(new Isin(shareClass), version, step.validator());
        return card(shareClass, version);
    }

    /** The cited page's extracted text, for the quote to be highlighted on it; 404 when the file is not here. */
    @GetMapping("/documents/{document}/pages/{page}")
    public ResponseEntity<Page> page(@PathVariable String document, @PathVariable int page) {
        return ResponseEntity.of(pageText.of(new DocumentId(document), page).map(Page::new));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    private CardView view(CardReview review) {
        Map<DocumentId, FundDocument.Type> types = fundDocuments.documents(review.card().shareClasses().getFirst())
                .stream()
                .filter(document -> document.type().isPresent())
                .collect(Collectors.toMap(FundDocument::documentId, document -> document.type().get(),
                        (first, second) -> first));
        return CardView.of(review, conditions, types);
    }

    /** Who takes a review step: kept in the rule's history and on the validated card. */
    public record ReviewStep(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String validator) {
    }

    /** The condition the validator read on the page, written as a card's JSON writes it. */
    public record Correction(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String validator,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> condition) {
    }

    public record Page(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text) {
    }
}
