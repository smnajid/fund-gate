package com.fundgate.rulecardpipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteCheckTest {

    private static final List<String> PAGES = List.of(
            "Unless otherwise decided by the board,\nUS persons may not\r\n subscribe for shares in the SICAV.",
            "Minimum initial investment: EUR 1 000 000. The board may reject any application.");

    @Test
    void a_quote_is_found_on_its_page_whatever_the_line_breaks() {
        assertThat(QuoteCheck.onPage("US persons may not subscribe for shares", PAGES, 1)).isTrue();
    }

    @Test
    void a_quote_typeset_with_no_break_spaces_is_found() {
        assertThat(QuoteCheck.onPage("EUR 1 000 000", PAGES, 2)).isTrue();
    }

    @Test
    void a_quote_from_another_page_is_not_found_on_the_cited_page() {
        assertThat(QuoteCheck.onPage("The board may reject any application", PAGES, 1)).isFalse();
    }

    @Test
    void a_quote_on_a_page_the_document_does_not_have_is_not_found() {
        assertThat(QuoteCheck.onPage("US persons may not", PAGES, 3)).isFalse();
        assertThat(QuoteCheck.onPage("US persons may not", PAGES, 0)).isFalse();
    }

    @Test
    void words_run_together_are_not_the_quote() {
        assertThat(QuoteCheck.onPage("USpersons may not", PAGES, 1)).isFalse();
        assertThat(QuoteCheck.onPage("S persons may", PAGES, 1)).isFalse();
    }

    @Test
    void a_quote_followed_by_punctuation_on_the_page_is_found() {
        assertThat(QuoteCheck.onPage("may reject any application", PAGES, 2)).isTrue();
    }

    @Test
    void a_quote_of_fewer_than_three_words_proves_nothing() {
        assertThat(QuoteCheck.onPage("US persons", PAGES, 1)).isFalse();
    }

    @Test
    void an_empty_quote_is_never_found() {
        assertThat(QuoteCheck.onPage("  ", PAGES, 1)).isFalse();
    }
}
