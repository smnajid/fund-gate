package fundgate.rulecard.application.port.out;

import java.util.List;
import java.util.Map;

import fundgate.rulecard.domain.Rule;

/**
 * Outbound port: the LLM that drafts rules from Fund Documentation. Public documents only; no Client
 * Profile ever crosses this port (ADR 0004). Returned citations point at original document pages.
 */
public interface RuleCardDrafter {

    String name();

    Draft draft(Request request);

    /** A PDF sent to the model. {@code originalPages.get(i)} is the original page of excerpt page i+1. */
    record Pdf(String docId, String title, List<Integer> originalPages, byte[] bytes) {

        public Integer originalPage(int excerptPage) {
            return excerptPage >= 1 && excerptPage <= originalPages.size() ? originalPages.get(excerptPage - 1) : null;
        }
    }

    record Request(String fund, Map<String, String> shareClasses, List<Pdf> documents) {
    }

    record Draft(List<Rule> rules, List<Usage> usage) {
    }

    record Usage(String model, double seconds, Long input, Long output, Long cacheWrite, Long cacheRead, double usd) {
    }
}
