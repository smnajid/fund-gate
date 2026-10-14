package fundeli5.rulecard.domain;

import java.util.List;

/** One document of Fund Documentation as page text, 1-based pages. */
public record FundDocument(String id, String title, List<String> pages) {

    public String page(int number) {
        return number >= 1 && number <= pages.size() ? pages.get(number - 1) : "";
    }
}
