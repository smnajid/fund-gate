package fundeli5.rulecard.application.port.in;

import java.util.List;
import java.util.Map;

import fundeli5.rulecard.application.ExtractionRun;

/** Inbound port: draft a cited Rule Card from a fund's documentation, ready for a Rule Card Validator. */
public interface DraftRuleCard {

    ExtractionRun draft(Command command);

    /**
     * @param fund       fund name for the Rule Card and the prompt
     * @param subFund    sub-fund name as printed in the umbrella prospectus, for page selection
     * @param shareClasses share class code to ISIN
     * @param prospectus  document id of the umbrella prospectus
     * @param kids        share class code to KID document id
     * @param pages       prospectus pages to send; empty means automatic page selection
     */
    record Command(String fund, String subFund, Map<String, String> shareClasses, String prospectus, Map<String, String> kids,
                   List<Integer> pages, int maxPages, String label) {
    }
}
