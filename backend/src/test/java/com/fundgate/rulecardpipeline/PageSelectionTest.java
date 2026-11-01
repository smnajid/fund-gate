package com.fundgate.rulecardpipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageSelectionTest {

    @Test
    void a_short_document_is_sent_whole() {
        List<String> pages = List.of("one", "two", "three");

        assertThat(PageSelection.select(pages, "Global Climate and Environment Fund", 40)).containsExactly(1, 2, 3);
    }

    @Test
    void an_umbrella_prospectus_keeps_front_matter_the_sub_funds_section_and_the_restriction_pages() {
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= 200; page++) {
            pages.add("Page " + page + " about fees and operations.");
        }
        pages.set(119, "Nordea 1 – Global Climate and Environment Fund\nInvestment objective: grow capital.");
        pages.set(122, "Share classes. I Institutional investors. Minimum initial investment EUR 75,000.");
        pages.set(137, "The board may reject any application. US persons may not subscribe. Restrictions apply.");

        List<Integer> selected = PageSelection.select(pages, "Global Climate and Environment Fund", 12);

        assertThat(selected).contains(1, 2, 3, 4, 5, 120, 121, 122, 123, 138).hasSizeLessThanOrEqualTo(12);
    }
}
