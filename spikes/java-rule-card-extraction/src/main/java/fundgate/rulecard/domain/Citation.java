package fundgate.rulecard.domain;

import java.util.List;

/**
 * A pointer from a rule to the exact passage of Fund Documentation it relies on. Pages are pages of the
 * original document, not of the excerpt sent to the LLM. {@code verified} is null until the quote check ran.
 */
public record Citation(String doc, Integer page, List<Integer> pages, String quote, Boolean verified) {

    public Citation verified(boolean ok) {
        return new Citation(doc, page, pages, quote, ok);
    }
}
