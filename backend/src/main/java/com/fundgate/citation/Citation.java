package com.fundgate.citation;

import com.fundgate.bankdata.Country;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.shareclasses.Isin;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** What a rule outcome relies on (issue #6, Citations). Every rule outcome carries at least one. */
public sealed interface Citation {

    /**
     * A passage of Fund Documentation: a page of the original document and its verbatim quote.
     * {@code quoteFoundOnPage} is empty until the quote check has run.
     */
    record Document(DocumentId document, int page, String quote, Optional<Boolean> quoteFoundOnPage) implements Citation {
    }

    /** A field of the Client Profile and its value at check time, e.g. {@code client.nationalities = [CH, US]}. */
    record ClientProfileField(String field, String value) implements Citation {
    }

    /** The bank's Fund Registration entry and when it was read. */
    record FundRegistration(Isin isin, Country country, String entry, Instant retrievedAt) implements Citation {
    }

    /** A dated rate from the bank's FX table. */
    record FxRate(String pair, BigDecimal rate, LocalDate date, String source) implements Citation {
    }
}
