# FundEli5

**Fund eligibility a five-year-old could follow, and an auditor could check.**
Can this client buy this fund? A cited answer in seconds, explained in plain words.

<!-- Resolves https://github.com/smnajid/fund-gate/issues/12. One A4 page when exported to PDF: keep each section to 2–5 lines. -->

## The problem

Every subscription request sends the fund dealing desk through a prospectus of about 400 pages, two regulators' rules (Swiss FinSA/CISA, Luxembourg MiFID II/UCITS/AIF) and the client file, to answer one question: may this portfolio buy this Share Class? Then the relationship manager still has to explain the "no" to the client.
The desk handles about 10,000 subscriptions a year, roughly 40 a working day (sponsor, 2026-10-15).
<!-- TODO #14: minutes per request, from the desk interview. Never invent it. -->

## What you'll see

A relationship manager emails the desk: put EUR 80,000 from two of Anna's portfolios into Nordea Global Climate, institutional class. FundEli5 answers each portfolio in seconds:

- **Not Eligible** for the advisory portfolio: it is not an institutional investor. **Eligible** for the discretionary mandate. Every reason cites the prospectus page.
- **Explained like you're five**: a short paragraph the RM can forward to the client, with a suggested class she *can* buy.
- **Form guided**: a checklist of the declarations and documents the subscription needs, pre-filled from the client's facts.
- **Questions answered**: "why not the I class?", "what would make her eligible?"

- **No email needed**: the same check from a structured request (portfolio, ISIN, amount), no AI involved. Every subscription the bank executes passes through a GX order to Negoce, so the next step is Negoce checking each fund Order with FundEli5 ([order flow](order-flow-integration.md)).

Closing: "which funds can Tom buy?" One US citizen, three funds, three different US Person definitions.

## How it works

```
 Fund docs ─► LLM drafts   ─► human       ─► engine     ─► reply, explained
              Rule Cards      validates      decides       like you're five
              (cited)                        (no LLM)
 ─────────────────────────────────────────────────────────────────────────
 Privacy Gate: client identity never reaches the LLM. Every check audited.
```

The AI drafts, a person validates, deterministic code decides. The explanation and the questions never change a Verdict.

## Already de-risked

- **Extraction on a real prospectus** (Nordea 1): 18/18 rules found, every quote checked on its page, 1–2 min and $0.2–1.2 per fund.
- **Claude on Microsoft Foundry** confirmed for PDF, citations and structured output.
- **A 26-case answer key** across Switzerland and Luxembourg, plus screens and architecture decided and recorded as ADRs.
- No product code yet, as the rules require: only throwaway spikes.

## Team we need

Five people. The desk that will use it is on the team.

| Seat | Profile | Status |
|---|---|---|
| Domain | Fund dealing desk member: plain-language wording, form checklist content, accepts the answer key, co-presents | from the desk |
| Pitch and platform | Adapters, Privacy Gate, audit trail, deployment, pitch | **filled: Mohamed** |
| Builder 1 | Java/Spring, LLMs and document pipelines: cited extraction from PDFs | open |
| Builder 2 | Java/Spring, loves deterministic logic and tests: rule engine, explanations, form checklist | open |
| Builder 3 | Angular plus LLM integration: the dealer screens, request parsing, reply drafting, Q&A | open |

Stack: Spring Boot 4 and Angular, Claude on Microsoft Foundry.

## How to join

Pick **FundEli5** when you apply, and tell <!-- TODO: internal email / Teams --> which seat you want.
