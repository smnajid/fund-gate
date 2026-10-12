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
fundgate.rulecard
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
