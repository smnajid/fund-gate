# Order flow integration: from desk tool to pre-trade control

Not built at the hackathon: this note maps where FundEli5 fits in the bank's order flow, for the pitch and later brainstorming. Source: exchange with Samuel (fund dealing desk), 2026-10-20, and a grilling session the same day. Vocabulary: [GLOSSARY.md](../../GLOSSARY.md).

## The bank's order flow

```
 PM ─► G2 (portfolio management) ─► ValidationEngine ─► Negoce (dealing desk) ─► booking
                                      ├─ client constraints (suitability, cash)
                                      ├─ credit risk
                                      └─ OrderRules (market: min amount, round lot)
```

- Portfolio managers often skip the email and create the **Order** directly in G2. **100% of executed subscriptions pass through G2 → Negoce**: no Order in Negoce, no execution.
- Before transmission, G2 calls the **ValidationEngine**, which fans the Order out to validators by domain. Each validator can **pass**, **block**, **warn** (the PM must justify; the ValidationEngine stores the justification and shows it back), or turn the constraint into an **asynchronous manual validation** by a named team on the ValidationEngine's own screen.
- Today the fund dealing desk re-checks eligibility by hand on each Order it receives in Negoce: the same work as an emailed request, triggered differently.

So email covers the question asked *before* an Order; the Order flow covers *every* execution.

## Roadmap

1. **Hackathon**: the email story stays the demo. One extra beat: a structured Subscription Request (portfolio, ISIN, amount, currency), no email, gets its Verdict straight from the engine through the same OpenAPI endpoint the dealer form uses, with no LLM call.
2. **Negoce calls FundEli5** (REST) on each incoming Order and shows the Verdict beside it. The desk's process is unchanged; only the manual check goes.
3. **FundEli5 behind OrderRules** in the ValidationEngine: every Order is checked before it leaves G2.

Stage 3 is possible because the Verdict involves no LLM ([ADR 0002](../adr/0002-no-llm-at-check-time.md)): a synchronous validator in an order pipeline must be fast, deterministic and auditable.

**Selling it to the desk.** Stage 3 moves the check upstream of the desk, which validates eligibility today. The desk goes from validating every Order to validating the rules: Fund Dealers and compliance remain the Rule Card Validators, and no Verdict exists without a card they validated ([ADR 0001](../adr/0001-llm-extracts-rules-engine-decides.md), [ADR 0003](../adr/0003-validator-marks-unmodelled-restrictions-noted.md)). The engine applies their judgement at the point where the Order is created.

## Verdict → ValidationEngine outcome

| Verdict | Outcome |
|---|---|
| Eligible | pass |
| Not Eligible on a Hard Restriction or Fund Registration | **block**, Justification returned to the PM |
| Not Eligible on a Target Market mismatch only | **warn**: the PM justifies (e.g. client-initiated, diversification); the ValidationEngine stores it |
| Needs Review | **manual validation** by the fund dealing desk |

Hard Restrictions never downgrade to a warning: they are binary by definition. A Target Market mismatch is a MiFID "distribute with care" signal, not a legal bar. This needs the Justification to say which kind of rule failed, which it already does rule by rule.

## Needs Review on an Order

The desk keeps one queue: the ValidationEngine's manual-validation screen. Each item deep-links to the FundEli5 answer sheet (Justification, evidence, Advisory Suggestions); the desk's decision goes back through the ValidationEngine asynchronously.

- The Reply Drafter has no role here: there is no email to answer.
- The ValidationEngine cannot send a question back to the PM, so accepted Requester Questions are asked outside the system, as an email follow-up is today.

## Overlap with existing validators

- **Minimum subscription.** OrderRules checks a minimum amount. It could delegate the per-Share-Class minimum to FundEli5; keeping or dropping the rule in OrderRules is the desk's decision, not ours.
- **Target Market.** Unknown whether the client-constraints validator (suitability) already covers the MiFID Target Market. If it does, the pitch says "consolidates", not "adds".

## Open questions

- Does the client-constraints validator cover the MiFID Target Market? (owner: Samuel)
- For discretionary mandates, is Target Market checked at mandate level or at client level? Affects the Target Market rules.
