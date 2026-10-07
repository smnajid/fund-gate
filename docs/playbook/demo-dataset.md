# Demo dataset

Resolves [Which synthetic Client Profiles make the demo?](https://github.com/smnajid/fund-gate/issues/8).

The synthetic data the team needs for rehearsal and the demo. This is a **spec**, not fixture files. The bank's data shape is only known at hour 0, so at hour 0 someone turns these rows into that shape. It takes about 30 minutes.

It has two tiers:
- **Demo cast**: 3 named clients the 5-minute story shows.
- **Coverage set**: every check below, cast included. Each row gives the Verdict and the per-rule outcomes it must produce, so it is also the answer key to rerun before going on stage.

Vocabulary: [GLOSSARY.md](../../GLOSSARY.md). Rule kinds and outcomes: [What is the Rule Card model?](https://github.com/smnajid/fund-gate/issues/6). Fund facts: [sample funds research](https://github.com/smnajid/fund-gate/blob/research/sample-funds/docs/research/sample-funds.md).

## Conventions

- **Synthetic client numbers** come from the reserved range **900001–900099**, so they can never be mistaken for a real client. Portfolio Reference = Legal Entity + client number + dossier number, e.g. `LOC90000101`.
- **Portfolio Ids** are random UUIDs, generated at hour 0. Only Portfolio Ids reach the LLM.
- Values use the **bank scales** below. The Scale Mapping is the only thing that translates them.
- *Assumed* marks a fact we chose, not one read from a source. Replace it with the bank's data at hour 0.

## Share Classes used

| Short name | Fund / class | ISIN | Class currency | Regime of fund |
|---|---|---|---|---|
| GCE-BP | Nordea 1 – Global Climate and Environment, BP-EUR | `LU0348926287` | EUR | LU UCITS |
| GCE-BI | Nordea 1 – Global Climate and Environment, BI-EUR | `LU0348927095` | EUR | LU UCITS |
| SIF-W | Nordea SIF – Global Private Equity, BW-SEK | `LU2607415275` | SEK (minimums in EUR, fixed 1 EUR = 10 SEK) | LU SIF |
| BNP-A | BNP PARIBAS (CH) Bonds CHF, A | `CH0112537516` | CHF | CH |
| BNP-AAC | BNP PARIBAS (CH) Bonds CHF, A AC | `CH0114292532` | CHF | CH |
| BNP-I | BNP PARIBAS (CH) Bonds CHF, I | `CH0112537557` | CHF | CH |
| SL-ICAP | Swiss Life iFunds (CH), I Cap | *ISIN to confirm* | CHF | CH |
| SL-AMCAP | Swiss Life iFunds (CH), AM Cap | *ISIN to confirm* | CHF | CH |
| SL-IA1 | Swiss Life iFunds (CH), I-A 1 | *ISIN to confirm* | CHF | CH |

Swiss Life class↔ISIN pairs are unverified (see the research). Pick one sub-fund, e.g. Bond Swiss Francs Domestic, and confirm the ISINs before the event.

## Bank scales and Scale Mapping

The bank scales are invented and deliberately differ from the Target Market scales, so the Scale Mapping does real work. At hour 0 the bank's real scales replace this one table.

| Bank field | Bank value | Target Market value |
|---|---|---|
| Risk profile | 1 Conservative | max SRI 2 |
| | 2 Income | max SRI 3 |
| | 3 Balanced | max SRI 4 |
| | 4 Growth | max SRI 5 |
| | 5 Dynamic | max SRI 7 |
| Knowledge & experience (per product type) | none | below basic |
| | basic | basic |
| | informed | informed |
| | expert | advanced |
| Ability to bear losses | no loss | no capital loss |
| | limited loss | limited capital loss |
| | full loss | can bear loss of entire capital |
| Investment horizon | years | compared with the KID's recommended holding period (pass if ≥ RHP) |

## Fund Registration

| Share Class | Country | Investor Classification | Registered | Source |
|---|---|---|---|---|
| GCE-BP, GCE-BI | CH | all | yes | *assumed* (Swiss representative named, prospectus p.146) |
| GCE-BP, GCE-BI | LU | all | yes | home jurisdiction |
| SIF-W | LU | well-informed | yes | home jurisdiction |
| SIF-W | CH | any | **no** | no Swiss representative named |
| BNP-A, BNP-AAC, BNP-I | CH | all (I: qualified only) | yes | home jurisdiction |
| BNP-A, BNP-AAC, BNP-I | LU | any | **no** | "no issue or redemption abroad" (prospectus p.16) |
| SL-* | CH | qualified | yes | "distribution approved: Switzerland" (appendix p.45) |
| SL-* | LU | any | **no** | same |

The bank's fund list replaces this table at hour 0 if it carries registration data.

## FX

| Pair | Rate | Date | Source |
|---|---|---|---|
| EUR/CHF | 1 EUR = 0.9350 CHF | 2026-11-02 | *assumed*, "bank FX table" |

The planned SEK rate is not needed. SIF minimums are stated in EUR, and the prospectus fixes 1 EUR = 10 SEK for converting them.

## Client Profiles

Fields follow the Rule Card model: client (client number) / client × Legal Entity / portfolio (dossier).

### Client level

| Client | Name | Residence | Nationalities | Tax residence | Place of birth | US Person / FATCA | Knowledge & experience |
|---|---|---|---|---|---|---|---|
| 900001 | Anna Steinmann | CH | CH | CH | Zürich | not a US Person / non-US | funds: informed |
| 900002 | Tom Whitfield | CH | CH, US | CH, US | Boston, US | US citizen / specified US person | funds: expert |
| 900003 | Claire Dupont | LU | FR | LU | Lyon, FR | not a US Person / non-US | funds: basic |
| 900004 | (coverage) | LU | LU | LU | Luxembourg | not a US Person | funds: expert, private equity: expert |
| 900005 | (coverage) | LU | BE | LU | Liège, BE | not a US Person | funds: informed, private equity: informed |
| 900006 | (coverage) | LU | DE | LU | Trier, DE | not a US Person | funds: informed, private equity: **none** |
| 900007 | (coverage) | CH | CH | CH | Bern | not a US Person | funds: expert |
| 900008 | (coverage) | LU | LU | LU | Luxembourg | not a US Person | funds: basic |
| 900009 | (coverage) | CH | CH | CH | Geneva | not a US Person | funds: expert |
| 900010 | (coverage) | CH | CH | CH | Basel | not a US Person | funds: basic |
| 900011 | (coverage) | CH | CH | CH | Lausanne | not a US Person | funds: informed |
| 900012 | (coverage) | CH | CH | CH | Lugano | **missing** | funds: basic |
| 900013 | (coverage) | SG | SG | SG | Singapore | not a US Person | funds: informed |
| 900014 | (coverage) | CH | CH | CH | Zürich | not a US Person | funds: basic |

### Client × Legal Entity

| Client | Legal Entity | Classification | Declarations |
|---|---|---|---|
| 900001 | LOC | FinSA retail | none |
| 900002 | LOC | FinSA professional | HNWI opt-out (FinSA art. 5(1)), 2024-05-14 |
| 900003 | LUX | MiFID retail | none |
| 900004 | LUX | MiFID professional (per se) | none |
| 900005 | LUX | MiFID retail | well-informed written confirmation, 2026-09-15 |
| 900006 | LUX | MiFID retail | well-informed written confirmation, 2026-09-20 |
| 900007 | LOC | FinSA professional | HNWI opt-out, 2023-02-01 |
| 900008 | LUX | MiFID retail | none |
| 900009 | LOC | FinSA professional | HNWI opt-out, 2022-11-10 |
| 900010 | LOC | FinSA retail | none |
| 900011 | LOC | FinSA retail | none |
| 900012 | LOC | FinSA retail | none |
| 900013 | SIN | (not covered) | none |
| 900014 | LOC | FinSA retail | none |

### Portfolio level

| Portfolio Reference | Mandate type (start) | Risk profile | Ability to bear losses | Horizon | Ref. currency |
|---|---|---|---|---|---|
| `LOC90000101` | advisory | 3 Balanced | full loss | 7y | CHF |
| `LOC90000102` | discretionary mandate (2021-03-01) | 4 Growth | full loss | 10y | EUR |
| `LOC90000201` | advisory | 4 Growth | full loss | 10y | CHF |
| `LUX90000301` | advisory | 2 Income | limited loss | 2y | EUR |
| `LUX90000401` | advisory | 5 Dynamic | full loss | 12y | EUR |
| `LUX90000501` | advisory | 2 Income | full loss | 5y | EUR |
| `LUX90000601` | advisory | 5 Dynamic | full loss | 12y | EUR |
| `LOC90000701` | advisory | 5 Dynamic | full loss | 12y | CHF |
| `LUX90000801` | advisory | 2 Income | limited loss | 5y | EUR |
| `LOC90000901` | advisory | 3 Balanced | full loss | 5y | CHF |
| `LOC90001001` | discretionary mandate (2019-06-01), not with Swiss Life | 3 Balanced | full loss | 7y | CHF |
| `LOC90001101` | discretionary mandate (2020-01-15) | 3 Balanced | full loss | 7y | CHF |
| `LOC90001201` | advisory | 2 Income | limited loss | 5y | CHF |
| `SIN90001301` | advisory | 3 Balanced | full loss | 5y | USD |
| `LOC90001401` | advisory | 2 Income | limited loss | 5y | CHF |

## Coverage set (answer key)

Rule columns:
- **Cat** investor category
- **US** US Person exclusion
- **Min** minimum initial subscription
- **Mand** mandate-only class
- **KID** KID required for retail
- **Unm** Unmodelled Restrictions
- **TM** Target Market
- **Reg** Fund Registration

Outcomes: P = Pass, F = Fail, R = Review, W = warning only (CH Target Market), – = no such rule on the card. Init. = client initiated the order.

Assumes [ADR 0003](../adr/0003-validator-marks-unmodelled-restrictions-noted.md): Nordea's "reject or close" and the SIF's "at AIFM discretion" clauses are validated as **Noted** (Pass).

| # | Portfolio | Share Class | Amount | Init. | Cat | US | Min | Mand | KID | Unm | TM | Reg | **Verdict** | What it shows |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `LOC90000101` Anna | GCE-BI | EUR 80,000 | no | **F** | P | P | – | P | P | P | P | **Not Eligible** | ★ Advisory retail portfolio is not institutional |
| 2 | `LOC90000102` Anna | GCE-BI | EUR 80,000 | no | P | P | P | – | P | P | P | P | **Eligible** | ★ Discretionary mandate counts as institutional (2010 Law art. 174, prospectus p.5) |
| 3 | `LOC90000101` Anna | GCE-BP | EUR 80,000 | no | P | P | P | – | P | P | P | P | **Eligible** | ★ The class the reply suggests for portfolio 01 |
| 4 | `LOC90000201` Tom | GCE-BP | CHF 2,000,000 | no | P | **P** | P | – | P | P | P | P | **Eligible** | ★ Reg S: US citizenship alone is not a US Person |
| 5 | `LOC90000201` Tom | GCE-BI | CHF 2,000,000 | no | **F** | P | P | – | P | P | P | P | **Not Eligible** | ★ HNWI opt-out is not an art. 174 institutional investor (*assumed reading: confirm with the desk*) |
| 6 | `LOC90000201` Tom | BNP-A | CHF 2,000,000 | no | P | **F** | P | – | P | – | W | P | **Not Eligible** | ★ "in particular investors with U.S. citizenship" |
| 7 | `LOC90000201` Tom | BNP-I | CHF 2,000,000 | no | P | **F** | P | – | P | – | W | P | **Not Eligible** | ★ Qualified and above CHF 1m, but US citizen |
| 8 | `LOC90000201` Tom | SL-ICAP | CHF 2,000,000 | no | P | **F** | – | – | – | P | W (no source) | P | **Not Eligible** | ★ IRC definition: any US citizen, dual nationals included |
| 9 | `LOC90000201` Tom | SIF-W | CHF 2,000,000 | no | P | P | P | – | P | P | W | **F** | **Not Eligible** | ★ Not registered in CH |
| 10 | `LUX90000301` Claire | GCE-BP | EUR 20,000 | no | P | P | P | – | P | P | **R** | P | **Needs Review** | ★ LU: outside positive Target Market (SRI 4 > max 3, 5y RHP > 2y, total loss > limited). Under LOC it would be Eligible with warnings |
| 11 | `LUX90000301` Claire | GCE-BI | EUR 20,000 | no | **F** | P | **F** | – | P | P | R | P | **Not Eligible** | ★ Retail, and below EUR 75k |
| 12 | `LUX90000401` | SIF-W | EUR 50,000 | no | P | P | **F** | – | P | P | P | P | **Not Eligible** | Below EUR 100k (W class) |
| 13 | `LUX90000501` | SIF-W | EUR 120,000 | no | P | P | P | – | P | P | **R** | P | **Needs Review** | Well-informed derived from written confirmation + ≥ EUR 100k. Negative Target Market on risk (SRI 6 > max 3) and horizon (10y RHP > 5y) |
| 14 | `LUX90000601` | SIF-W | EUR 120,000 | no | P | P | P | – | P | P | **F** | P | **Not Eligible** | LU: negative Target Market on knowledge & experience gives Fail |
| 15 | `LOC90000701` | SIF-W | EUR 150,000 | no | P | P | P | – | P | P | W | **F** | **Not Eligible** | Not registered in CH |
| 16 | `LOC90000701` | SIF-W | EUR 150,000 | **yes** | P | P | P | – | P | P | W | **R** | **Needs Review** | Reverse solicitation: the dealer records evidence |
| 17 | `LUX90000801` | BNP-A | CHF 50,000 | no | P | P | P | – | P | – | P | **F** | **Not Eligible** | "No issue or redemption abroad" |
| 18 | `LOC90000901` | SL-AMCAP | CHF 500,000 | no | **F** | P | – | **F** | – | P | W (no source) | P | **Not Eligible** | HNWI opt-outs excluded, and no Swiss Life mandate |
| 19 | `LOC90000901` | SL-ICAP | CHF 500,000 | no | P | P | – | – | – | P | W (no source) | P | **Eligible** | Same client, open class. Missing Target Market source is a warning only in CH |
| 20 | `LOC90001001` | SL-IA1 | CHF 300,000 | no | **F** | P | – | **F** | – | P | W (no source) | P | **Not Eligible** | Art. 10(3ter) mandate clients excluded |
| 21 | `LOC90001001` | SL-ICAP | CHF 300,000 | no | P | P | – | – | – | P | W (no source) | P | **Eligible** | 3ter mandate clients are qualified for I Cap |
| 22 | `LOC90001101` | GCE-BI | CHF 70,000 | no | P | P | **R** | – | P | P | P | P | **Needs Review** | CHF 70,000 / 0.9350 = EUR 74,866: below EUR 75k by 0.2%, within 5% |
| 23 | `LOC90001201` | BNP-A | CHF 50,000 | no | P | **R** | P | – | P | – | W | P | **Needs Review** | US Person / FATCA status missing: Review, never Fail |
| 24 | `SIN90001301` | GCE-BP | EUR 50,000 | no | | | | | | | | | **Needs Review** | Regime not covered (SIN) |
| 25 | `LOC90001401` | BNP-AAC | CHF 50,000 | no | | | | | | | | | **Needs Review** | Only a Draft Rule Card exists for this class |
| 26 | `LOC90000102` Anna | GCE-BI | *missing* | no | P | P | **R** | – | P | P | P | P | **Needs Review** | The request names no amount |

★ = demo cast. Rows 4–9 are Tom's reverse lookup, one row per Share Class.

"Within 5%" is read as: an amount below the minimum by at most 5% gives Review; further below gives Fail; at or above gives Pass.

## Demo cast

### Anna Steinmann: same client, same fund, two portfolios (hero)

The email lists two Portfolio References and no client data. The system runs one check per reference and returns a Verdict for each (rows 1 and 2). The drafted reply suggests GCE-BP for portfolio 01 (row 3).

> **From:** Marc Aebi (relationship manager)
> **To:** Fund dealing desk
> **Subject:** Subscription Nordea Global Climate – LOC90000101, LOC90000102
>
> Hi desk,
> The client would like to put EUR 80,000 into the Nordea Global Climate fund, institutional class, from portfolios LOC90000101 and LOC90000102. Can you confirm they can subscribe?
> Thanks, Marc

### Tom Whitfield: one client, three US Person definitions (closing flourish)

There is no email. The dealer opens `LOC90000201` in the dealer screen with an amount of CHF 2,000,000 and runs "what can this portfolio buy?". This is deterministic and needs no LLM. The table of rows 4–9 shows the same US citizen passing Nordea's Reg S test and failing BNP's and Swiss Life's.

### Claire Dupont: Target Market is binding in Luxembourg

The email is in French and names the fund but no Share Class. That means "check every class of that fund" (rows 10 and 11). The reply recommends BP-EUR and lists what the dealer must review.

> **De :** Sophie Lentz (conseillère)
> **À :** Fund dealing desk
> **Objet :** Souscription Nordea Global Climate – LUX90000301
>
> Bonjour,
> Ma cliente (LUX90000301) souhaite investir 20 000 EUR dans Nordea 1 Global Climate and Environment. Pouvez-vous valider ?
> Merci, Sophie

The advisory lane turns row 10's Needs Review into what the dealer should check and which evidence to file ([ADR 0005](../adr/0005-advisory-llm-lane-after-the-engine.md)).

## Guard email (no coverage row)

Requesters identify portfolios by Portfolio Reference only ([architecture](architecture.md)). This email breaks the convention, so the Privacy Gate finds a known client name, makes no LLM call, and the dealer enters the request by hand.

> **From:** Marc Aebi
> **Subject:** Nordea Global Climate for Anna Steinmann
>
> Hi desk, can Anna Steinmann (LOC90000101) subscribe EUR 80,000 to Nordea Global Climate BP-EUR?

## Coverage email (row 26)

> **From:** Marc Aebi
> **Subject:** LOC90000102 – Nordea Global Climate BI-EUR
>
> Please check whether LOC90000102 can subscribe to Nordea Global Climate BI-EUR. Amount to follow.

These emails are drafts. Revise them once [How does the fund dealing desk answer subscription requests today?](https://github.com/smnajid/fund-gate/issues/14) brings real samples.

## At hour 0

1. Map the canonical fields above onto the bank's export, and swap the Scale Mapping for the bank's scales.
2. Generate fixtures in the bank's shape, with fresh Portfolio Ids.
3. Replace the Fund Registration and FX tables with the bank's if it provides them.
4. Run the coverage set; every row must match before the demo is rehearsed.
