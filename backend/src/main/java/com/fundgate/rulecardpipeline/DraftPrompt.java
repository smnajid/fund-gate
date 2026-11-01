package com.fundgate.rulecardpipeline;

import com.fundgate.funddocuments.FundDocument;
import com.fundgate.llm.LlmRequest;
import com.fundgate.shareclasses.ShareClass;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The one request that drafts a fund's rules: public Fund Documentation only, never a Client Profile (ADR 0004).
 * Ported from the extraction spike's prompts, sending page text instead of PDFs, since the egress client takes text.
 * Any change here changes the request, so every recorded answer of replay mode must be recorded again.
 */
final class DraftPrompt {

    static final String SYSTEM = """
            You draft a Rule Card from Fund Documentation for a private bank's fund dealing desk.
            A Rule Card is the set of Hard Restrictions and Target Market criteria deciding whether a client may \
            subscribe to one Share Class. A human validator reviews every rule you draft against its quotes.

            Rule kinds (use only these):
            - investor_category: which investor categories may buy the class; allowed_categories from all, retail, \
            professional, institutional, qualified, well_informed.
            - us_person_exclusion: US persons are excluded; us_person_definition is reg_s, fatca, irc_citizenship \
            or mixed; excludes_us_citizens says whether US citizenship alone excludes.
            - minimum_initial_subscription: amount and currency, as the document states them (0 if none).
            - mandate_only_class: the class is reserved to discretionary-mandate clients; manager names the \
            manager if the document names one, else null. Draft it only for a class that is reserved.
            - kid_required_for_retail: kid_available says whether a PRIIPs KID exists for the class.
            - target_market: one rule per dimension: client_type (all, retail, professional, institutional), \
            knowledge_experience (basic, informed, advanced), loss_bearing (no_loss, limited_loss, full_loss), \
            risk_sri and horizon_years (plain numbers); polarity is positive (who the fund is for) or negative \
            (who it is not for).
            - unmodelled_restriction: any other restriction on who may subscribe, or on accepting a subscription, \
            that fits none of the kinds above.
            share_class is the class's name, or "*" for a rule that applies to every class of the fund.
            When two documents contradict each other on a rule, never resolve it: draft each document's rule with \
            the value that document states, set contradiction on both, and cite each passage.
            Ignore fees, investment policy, tax and operations unless they restrict who may subscribe.
            Fill only the fields relevant to the kind; leave the others out.

            Every rule needs at least one citation: the document id, the page number given in the page tag, and a \
            quote copied verbatim from that page (one contiguous passage, no ellipsis).

            Answer with JSON only, in this shape:
            {"rules": [{"share_class": "...", "kind": "...", "allowed_categories": [...], \
            "us_person_definition": "...", "excludes_us_citizens": false, "amount": 0, "currency": "...", \
            "manager": null, "kid_available": true, "dimension": "...", "polarity": "...", "value": "...", \
            "contradiction": false, "confidence": 0.0, "summary": "...", \
            "citations": [{"document": "...", "page": 1, "quote": "..."}]}]}""";

    private DraftPrompt() {
    }

    /** One selected document: its metadata, every page's text, and the pages sent. */
    record Pages(FundDocument document, List<String> text, List<Integer> selected) {
    }

    static LlmRequest request(String model, String fundName, List<ShareClass> classes, List<Pages> documents) {
        String shareClasses = classes.stream()
                .map(shareClass -> shareClass.shareClassName() + " (" + shareClass.isin() + ")")
                .collect(Collectors.joining(", "));
        StringBuilder prompt = new StringBuilder()
                .append("Fund: ").append(fundName).append('\n')
                .append("Share classes: ").append(shareClasses).append("\n\n")
                .append("Documents. Only the pages listed were selected; each page number is the page of the ")
                .append("original document.\n");
        for (Pages pages : documents) {
            FundDocument document = pages.document();
            prompt.append("\n<document id=\"").append(document.documentId())
                    .append("\" type=\"").append(document.type().map(type -> type.name().toLowerCase()).orElse("other"))
                    .append("\" language=\"").append(document.language().orElse("unknown"))
                    .append("\" pages=\"").append(pages.text().size()).append("\">\n");
            for (int page : pages.selected()) {
                prompt.append("<page document=\"").append(document.documentId())
                        .append("\" number=\"").append(page).append("\">\n")
                        .append(pages.text().get(page - 1).strip()).append("\n</page>\n");
            }
            prompt.append("</document>\n");
        }
        prompt.append("\nReturn the Rule Card of ").append(fundName).append(" as JSON.");
        return new LlmRequest(model, SYSTEM, prompt.toString());
    }
}
