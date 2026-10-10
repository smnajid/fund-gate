# Reference architecture

Resolves [What is the reference architecture and its module boundaries?](https://github.com/smnajid/fund-gate/issues/9).

Stack: Spring Boot 4 for both runtimes (the pipeline is a command-line mode of the same app, run as its own process) and Angular for both screens, with the Angular client generated from the backend's OpenAPI spec; Python only where it adds real value, which today is nowhere ([ADR 0006](../adr/0006-spring-boot-and-angular-python-only-for-real-added-value.md)). Screens: [screens.md](screens.md). Every module below is a seam with a typed interface, so one person can own it. Vocabulary: [GLOSSARY.md](../../GLOSSARY.md). Decisions: [ADR 0004](../adr/0004-two-runtimes-one-llm-egress.md) (shape and privacy boundary), [ADR 0005](../adr/0005-advisory-llm-lane-after-the-engine.md) (advisory lane), [ADR 0002](../adr/0002-no-llm-at-check-time.md) (the Verdict involves no LLM).

## Shape

Two runtimes, split on whether they touch client data, sharing one store.

```
 OFFLINE: public data only                 ONLINE: dealer app, client data stays inside
 ┌───────────────────────────┐             ┌─────────────────────────────────────────────────┐
 │ Rule Card pipeline (CLI)  │             │ Intake (paste / .eml)                           │
 │  page selection           │             │  → Privacy Gate (Portfolio Ref → Portfolio Id,  │
 │  extract (LLM, Citations) │             │     fail-closed guard, fact allow-list)         │
 │  quote-on-page check      │             │  → Request Parser (LLM) → Share Class catalog   │
 └─────────────┬─────────────┘             │  → Rule Engine (deterministic)                  │
               │ Draft cards               │      ← Client Profile, CRM, Fund Registration,  │
               ▼                           │        Scale Mapping (adapters)                 │
        ┌──────────────┐                   │  → Advisory lane (LLM, Needs Review only)       │
        │ Store        │◄─────────────────►│  → Reply Drafter (LLM, template fallback)       │
        │ Rule Cards,  │                   │  → Check Record (append-only)                   │
        │ Scale Mapping│                   │ Validation UI (Draft → Validated)               │
        │ Check Records│                   │ Reverse lookup (engine × every catalog class)   │
        └──────────────┘                   │ LLM egress client: the only path to Foundry     │
                                           └─────────────────────────────────────────────────┘
```

## Modules

| Module | Runtime | Input → output | LLM? |
|---|---|---|---|
| **Rule Card pipeline** | offline CLI | Fund Documentation PDF → Draft Rule Card (cited), quotes checked on their page. Ported from the [extraction spike](https://github.com/smnajid/fund-gate/tree/prototype/rule-card-extraction/spikes/rule-card-extraction) | yes, public documents only |
| **Share Class catalog** | online | fund/class mention or ISIN → Share Class(es); a fund named without a class fans out to all its classes. The same matcher serves the reverse-lookup search ([screens](screens.md#reverse-lookup-what-can-this-portfolio-buy)) | no |
| **Validation UI** | online | Draft card → corrected, Validated card; Unmodelled Restrictions marked Blocking/Noted (ADR 0003); who and when kept per version | no |
| **Intake** | online | pasted text or `.eml` → raw request text | no |
| **Privacy Gate** | online | raw text → pseudonymised text + local Portfolio Reference ↔ Portfolio Id map; client facts → allow-listed fields for one call | no |
| **Request Parser** | online | pseudonymised text → editable list of Subscription Requests (Portfolio Id, Share Class mention, amount, currency, client-initiated) | yes |
| **Rule Engine** | online | Subscription Request + Validated Rule Card + Client Profile + Fund Registration + Scale Mapping → Verdict + Justification | **never** |
| **Reverse lookup** | online | portfolio + amount → engine run against every Share Class in the catalog; Draft-only or card-less classes give Needs Review, as a single check would | no |
| **Advisory lane** | online | Needs Review Justification + allow-listed facts → labelled "what to check / evidence to file" suggestions | yes |
| **Reply Drafter** | online | Verdict + Justification + allow-listed facts → reply in the request's language; Portfolio Ids swapped back afterwards; template per Verdict as fallback | yes |
| **Check Record** | online | everything a check read and produced → append-only record | no |
| **LLM egress client** | both | the only code that calls Foundry; the online side accepts only Privacy Gate output | — |
| **Adapters** | online | Client Profile (bank's hour-0 export → canonical), CRM (Portfolio Reference ↔ Portfolio Id; mock), Fund Registration (static table) | no |

Interfaces are agreed at hour 0 as JSON schemas: **Rule Card**, **Client Profile**, **Subscription Request**, **Check Record**. Everyone builds against fixtures from [demo-dataset.md](demo-dataset.md).

## Data flow of one request

1. The Fund Dealer pastes the Requester's email. Requesters identify portfolios by **Portfolio Reference only, never client data** (*provisional: to confirm with the business partners and the desk*).
2. The Privacy Gate swaps every `[A-Z]{3}\d{8}` match for its Portfolio Id through the CRM, then re-scans. If a known client name or a reference the CRM does not know is still there, **no LLM call is made** and the dealer fills the request form by hand.
3. The Request Parser turns the pseudonymised text into Subscription Requests. The dealer sees them in an editable form; several references give several checks; anything unresolved becomes Needs Review "request unclear", never a guess.
4. The Rule Engine runs each check, with no LLM. A Share Class with only a Draft card, or no card, gives Needs Review ("Rule Card not validated" / "Fund not covered"); a Draft card never gives Eligible or Not Eligible.
5. On Needs Review, the advisory lane suggests what to check and which evidence to file. It may only **downgrade** a Verdict, never toward Eligible; its output is labelled AI-generated and a human accepts it.
6. The Reply Drafter writes the reply. The Portfolio Ids are swapped back to Portfolio References locally.
7. A Check Record is written.

## Privacy boundary

- **Never sent to an LLM**: client name, Portfolio Reference, address, phone, email, exact birth date, place of birth, balances.
- **Allow-listed pseudonymised facts**, only the fields each call needs: Portfolio Id, Legal Entity code, Investor Classification, mandate type, residence country, nationalities, tax residence, US Person / FATCA flags, knowledge and experience, risk profile, ability to bear losses, horizon. Derived values when needed (age band, "US-born yes/no"). *Permitted pending written confirmation from the sponsor.*
- Minimum per call, because combinations of quasi-identifiers can re-identify private-bank clients.
- Each Check Record logs exactly which fields went out.

## Pre-computed vs live

| Pre-computed | Live |
|---|---|
| Rule Cards for the 4 sample funds (1–2 min, $0.2–1.2 each) | Validating one Draft card on stage |
| Share Class catalog | Intake → Gate → Parser → Engine → advisory lane → Reply |
| Scale Mapping, Fund Registration, fixtures | Reverse lookup |

**Versioning.** A new Fund Documentation edition gives a new Draft version. The previous Validated version stays in force until the new one is validated. Versions are never edited, only superseded.

## Audit

One append-only **Check Record** per Eligibility Check: the Subscription Request, Rule Card version, Scale Mapping version, snapshot of the Client Profile fields read, engine version, Verdict, Justification, and for every LLM call the gated prompt, the response and the model id. The dealer UI has a "View Check Record" panel showing exactly what the LLM saw. Replaying a past Verdict (deterministic engine + pinned versions) is mentioned on the production slide, not built.

## Roles

- **Fund Dealer** runs checks and sends replies.
- **Rule Card Validator** (compliance or a senior dealer) validates cards. Separation of duties in production; one person plays both on stage.

## Runtime

- **Demo**: runs locally on our laptops (one compose file: dealer app + store; the pipeline as a CLI against the same store). Claude on Microsoft Foundry through the egress client. Fallbacks by config switch: our own Anthropic key, then a recorded-responses mode for offline rehearsal.
- **Production path (slide only)**: Container Apps, Entra ID SSO for dealers and validators, Key Vault, Foundry over a private endpoint, logs to Log Analytics, Check Record store as the audit source. Next lanes: NER-based redaction is not needed while Requesters send references only; advisory options (b) suggest Blocking/Noted per client and (c) propose facts from free-text KYC notes; Microsoft Graph mailbox intake.

## Ownership (5 people)

| Person | Owns |
|---|---|
| 1 | Rule Card pipeline (port the spike), Share Class catalog, seeding the 4 funds |
| 2 | Rule Engine, Scale Mapping, reverse lookup, test-first against the coverage set |
| 3 | Dealer UI and Validation UI |
| 4 | Intake, Privacy Gate, LLM egress, Request Parser, Reply Drafter, advisory lane |
| 5 | Adapters, Check Record, compose/deployment, pitch and production slide |

Person 4 carries the most; the advisory lane is cut first if time runs short. The hour-by-hour plan belongs to [How does a team of 5 run the 36 hours?](https://github.com/smnajid/fund-gate/issues/11).
