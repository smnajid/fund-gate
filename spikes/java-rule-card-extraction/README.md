# PROTOTYPE: Rule Card extraction on Spring Boot 4, Spring AI, Java 25

Throwaway spike for [ticket #31](https://github.com/smnajid/fund-gate/issues/31). It ports the Python spike from [#7](https://github.com/smnajid/fund-gate/issues/7) (branch `prototype/rule-card-extraction`) to the hackathon stack. It runs on the same fund, against the same gold Rule Card, with the same scoring. Not hackathon product code.

## Run

```bash
# fetch the Nordea 1 GCE prospectus + 2 KIDs into docs/ (gitignored); URLs in docs/research/sample-funds.md
./run.sh --check-gold        # PDFBox text: every gold quote on its page, auto scope covers gold pages
./run.sh --dry-run           # page selection only
./run.sh --pages=3,5,16,123,124,134,138,139,146 --label=gold-pages   # Claude plan (headless claude -p)
./run.sh --label=auto        # automatic page selection
SPRING_PROFILES_ACTIVE=spring-ai ./run.sh --label=api   # Anthropic API / Foundry; needs ANTHROPIC_API_KEY
./run.sh --score             # rescore the latest run
./run.sh --recall=page-recall-key-unseen.json --select=jev-page --max-pages=25   # Jev ranks the pages (#39); TYPESAFE_API_KEY
```

Each run writes `runs/<stamp>-<label>.json` (the Draft Rule Card plus usage) and a `.md` score.

## Shape

```
fundeli5.rulecard
├── domain                      pure Java: RuleCard (always DRAFT out of extraction), Rule, Citation,
│                               FundDocument, PageSelection, QuoteCheck
├── application                 DraftRuleCardService: select pages → drafter → quote check → Draft card
│   ├── port/in   DraftRuleCard
│   └── port/out  FundDocuments, RuleCardDrafter (the LLM egress), ExtractionRuns
├── adapter
│   ├── in/cli                  PipelineCommand (ApplicationRunner, no web server)
│   └── out
│       ├── pdf                 PdfBoxFundDocuments (page text cache, page excerpts)
│       ├── llm/claudeplan      ClaudePlanDrafter: claude -p --json-schema, model writes {file,page,quote}
│       ├── llm/springai        SpringAiCitationsDrafter: pass 1 PDF + Citations, pass 2 outputSchema
│       └── store               JsonExtractionRuns
├── PipelineWiring              the only class that knows which adapter plugs into which port
└── spike/GoldScorer            port of score.py, spike-only
```

`domain` and `application` import no Spring, Jackson, PDFBox or Spring AI classes.

## Results (2026-10-12, Claude plan, Opus 5.5)

| Run | Prospectus pages | Rules found | Correct values | Quotes on the cited page | Time | Cost* |
|---|---|---|---|---|---|---|
| hand-picked pages | 9 | 18/18 | 18/18 | 38/38 | 82 s | $0.62 |
| automatic page selection | 27 of 944 | 18/18 | 18/18 | 38/38 | 131 s | $1.81 |
| automatic, with a sub-fund name bug (23 pages) | 23 | 18/18 | 18/18 | 35/35 | 78 s | $0.96 |

\* Claude Code's list-price estimate, not a bill. Both traps handled every time (mandate-only BI avoided, KID-vs-prospectus contradiction flagged). Same scores as the Python spike's best run.

- PDFBox replaces pypdf: all 24 gold quotes found on their page, 944 pages to text in 5.5 s, auto scope = 27 pages, as in Python.
- The Spring AI adapter compiles and wires (`spring-ai` profile) but was **not run live**: no API credits.
- Spring AI 2.0.1's Anthropic module sits on the official Anthropic Java SDK and supports PDF documents with Citations (`AnthropicCitationDocument`) and structured output (`outputSchema`). But `ChatModel` joins all text blocks and returns citations as one flat metadata list, so it loses which passage supports which statement. Pass 1 therefore calls `AnthropicChatModel.getAnthropicClient()` (the SDK client Spring AI configured) directly; pass 2 uses `ChatModel`.

## Page recall on unseen documents (ticket #32, 2026-10-12, no LLM)

`./run.sh --recall` scores `PageSelection` against `page-recall-key.json` (page plus a quote that must sit on it, per
Hard Restriction; the funds' PDFs are downloaded into `docs/` from the URLs in `docs/research/sample-funds.md` on
`research/sample-funds`). `./run.sh --sweep` runs every Nordea 1 sub-fund (`nordea1-subfunds.json`) against the shared
rule pages 3, 5, 123, 124, 138 plus its own description page.

- Before the fixes: 25/26 pages over 9 documents (Nordea 1, Nordea SIF GPE, BNP (CH) Bonds CHF in EN and FR, 4 KIDs,
  Swiss Life iFunds). The one miss: Swiss Life p.2, the FINMA exemption that says there is no KID (no Target Market
  source). The French prospectus scored 4/4 with English keywords (fund name + "restrict"); the KIDs are 3 pages.
- Sweep: 89/89 Nordea 1 sub-funds keep every expected page, 25–32 pages each.
- Fund-name spelling is the real weak point: "Global Climate & Environment Fund" or a share-class suffix silently drops
  the sub-fund's own description page. Now a hard error on documents over 100 pages.
- Front matter (pages 1–5) is now always kept: it fixes the Swiss Life miss. After both fixes: 26/26.
- Small documents barely shrink (BNP 26 of 34 pages, Swiss Life 14 of 47, SIF 18 of 63); selection only pays on umbrellas.
- `QuoteCheck` normalised whitespace with Java `\s`, which misses the no-break spaces French typesetting uses
  ("1 000 000", "n’y a pas"); true French quotes failed. Now `[\s\p{Z}]`.

### 20 unseen fund documents (ticket #32, follow-up)

`page-recall-key-unseen.json`: 22 documents downloaded into `docs/x-*.pdf` (not committed; URLs in `unseen-sources.txt`),
12 publishers (Allianz GI, Amundi/Lyxor, BlackRock ×2, Candriam EN+FR, UBP, Swiss Rock, DWS ×2, JPMorgan ×2,
La Française, M&G, Pictet DE, Robeco DE, Vontobel CH+LU, Swisscanto KID DE, Schroders, Goldman Sachs). One sub-fund
per umbrella; per rule kind (description, US Person, share-class eligibility, minimum, sometimes Target Market) the
page with a quote verified on it, plus `alt` pages that state the same rule. Key pages were found with different,
multilingual searches from the selector's keywords and confirmed by reading the snippet.

| | v1 (`PageSelection`, Nordea-tuned) | v2 (`spike/PageSelectionV2`, `--v2`) |
|---|---|---|
| unseen, 19 docs with text, 74 rules | **40/74**; 7 docs stop at the name guard | **72/74** at 40 pages |
| demo set, 9 docs, 35 rules | 35/35 | 35/35 |
| scope, unseen | 10–40 | 3–40 (median ~28) |

- v1 is Nordea-tuned: "own page" = name in the first 400 chars next to English "investment objective"; DE/FR and
  other layouts never match, and its English keywords miss "Minimum Subscription", "Class X … reserved",
  "Mindestzeichnung".
- v2: front matter; sub-fund section = first page where the name is followed by an objective/currency heading
  (EN/DE/FR), plus 3 pages; the 10 pages naming it most; then pages ranked by the number of **distinct** EN/DE/FR
  rule keywords (counting occurrences let AGIF's repeated "Investment Restrictions" table crowd out the rule pages).
  Designed after seeing v1's failures on this set, so 72/74 is optimistic.
- Nordea 1 sweep with v2: 89/89 sub-funds, 40 pages each.
- v2 misses: AGIF p.26 "3.3 Minimum Investment Amount" (one keyword only) and BlackRock BGF's description page (no
  heading after the name; its rule pages were all found).
- 2 of 21 PDFs have **no text layer** (Schroders 1,428 pp, GS supplement): no keyword selection and no quote check is
  possible; they need OCR first (Document Intelligence, #13's pass 0).

## Page selection by Jev (ticket #39, 2026-10-16)

[TypeSafe Jev](https://docs.typesafe.ai/introduction) ranks the pages for each of six rule kinds: the sub-fund's own
description, US Person, investor eligibility, minimum, target market, other restriction. Then the same selection as the
Elasticsearch prototype: pages 1–5, then round-robin over the six rankings until the budget is used. Documents no longer
than the budget are sent whole. `spike/JevClient`, `spike/JevPageRanking`; rankings cached in `jev-rankings/`, so other
budgets cost nothing.

- **jev-page**: one request per page, one yes/no (noul) question per rule kind, the page as state. Scores compare across
  the whole document. This is the pattern of JEV's own RAG-classification and re-ranking cookbooks.
- **jev-choice**: per rule kind, a choice question whose options are the pages plus "none". A question holds about 20
  pages (32k tokens), so the pages play a knockout: the top 3 of each batch go on until one batch is left.

Rule recall (pages selected, summed over the documents with text):

| budget | set | v2 (keywords) | jev-page | jev-choice |
|---|---|---|---|---|
| 40 | demo, 35 rules | 35/35 | 35/35 | 35/35 |
| 40 | unseen, 74 rules | 72/74 | **74/74** | **74/74** |
| 25 | demo | 35/35 (104 p) | 35/35 (137 p) | 35/35 (137 p) |
| 25 | unseen | 69/74 (416 p) | 73/74 (453 p) | **74/74** (453 p) |
| 15 | demo | 31/35 (82 p) | **35/35** (87 p) | 34/35 (87 p) |
| 15 | unseen | 50/74 (279 p) | 69/74 (273 p) | **71/74** (273 p) |
| 10 | demo | 29/35 (80 p) | **31/35** (62 p) | 30/35 (62 p) |
| 10 | unseen | 36/74 (239 p) | 60/74 (183 p) | **67/74** (183 p) |

| | jev-page | jev-choice |
|---|---|---|
| cost, all 27 documents (~8,000 pages) | $0.40 | $1.84 |
| cost, Nordea prospectus (944 pages) | $0.05, 944 requests, 32 s | $0.20, 328 requests, 18 s |
| tokens per page | ~1,250 (state counted once for 6 questions) | ~5,000 (6 questions, plus later rounds) |

- Both Jev variants beat the keywords at every budget, and the gap grows as the budget shrinks: at 15 pages the
  keywords lose 24 of 74 rules, Jev loses 3–5. v2 was tuned on the unseen set; the Jev questions were written once from
  the rule kinds and not tuned. v2's fixed parts (front matter, 4 section pages, 10 name pages) eat a small budget.
- German and French pages were not a problem at 25 pages or more, despite the docs saying English is Jev's best language.
  Several of the 10-page misses are DE (Mindestzeichnung, Anlegerart).
- **jev-choice** ranks better (74/74 at 25 pages), at about 4–5× the tokens of jev-page. At these prices both are noise
  next to the Citations call ($1–2 per Nordea run).
- **jev-page**'s one miss at 25 pages: JPMorgan's US Person rule (p.45, also stated on pp. 292 and 303; none selected).
- Remaining misses are tables that only make sense with their heading on the page before (JPMorgan p.280 "base share
  classes", Amundi p.81 minimums) and Nordea p.138 at 10–15 pages.
- No text layer still means no selection (Schroders, GS supplement): Jev reads text, OCR first.

### End to end on Nordea: Rule Card from Jev's pages (2026-10-16, Claude plan, Opus 5.5)

Jev choice's 25 pages (`--pages=1,2,3,4,5,6,16,67,106,123,124,131,136,137,138,210,248,249,857,859,860,861,880,936,944
--label=jev-choice-25`), same drafter and gold card as the runs above:

| Run | Prospectus pages | Rules found | Correct values | Quotes on the cited page | Time | Cost* |
|---|---|---|---|---|---|---|
| hand-picked pages | 9 | 18/18 | 18/18 | 38/38 | 82 s | $0.62 |
| keyword selection (v1) | 27 of 944 | 18/18 | 18/18 | 38/38 | 131 s | $1.81 |
| **Jev choice, 25 pages** | 25 of 944 | **18/18** | **18/18** | **40/40** | 90 s | $1.00 + $0.20 Jev |

\* Claude Code's list-price estimate, not a bill. Both traps handled (mandate-only BI avoided, KID-vs-prospectus
contradiction flagged). Jev's pages skip three hand-picked pages (134, 139, 146); the card lost nothing, as the KIDs and
pages 136–138 carry the same rules. One run on one fund: the Citations API path (`spring-ai` profile) is still untested
live.
