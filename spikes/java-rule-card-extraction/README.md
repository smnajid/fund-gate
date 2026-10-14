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
