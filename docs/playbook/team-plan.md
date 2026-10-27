# Team plan: one builder, four specialists

Resolves [How does a team of 5 run the 20 build hours?](https://github.com/smnajid/fund-gate/issues/11). Replanned on 2026-10-27, when the team was confirmed with one technical member. The one-slide version for the kickoff is [kickoff-slide.md](kickoff-slide.md).

## What changed

| Before | Now |
|---|---|
| 3 builders to recruit, the lead, one desk member | Mohamed is the only builder. Four specialists from the business side. Samuel and Tania (fund dealing desk) advise 30 min a day, before and during the event |
| No product code before the event | No rule forbids it (checked with the organisers, 2026-10-26). Mohamed builds the Must tier before the event, on fixtures |
| 20 hours to build the app | 20 hours to move the app into the bank's environment, add the Should tier and rehearse |

One builder cannot build three builders' work in 20 hours, so the build moves before the event. The bank adapters stay at the event: they can only be written in the bank's internal environment ([ADR 0008](../adr/0008-bank-data-enters-through-canonical-contracts.md)).

## The window

| When | What |
|---|---|
| Tue 27 Oct – Tue 3 Nov | Pre-build: Mohamed builds on fixtures; the team prepares content, data and the pitch |
| Wed 4 Nov, day 1 | 9:00–22:00 (13 h) build, then everyone goes home and sleeps |
| Thu 5 Nov, day 2 | 8:00–15:00 (7 h) build; 15:00–16:00 finalise the presentation, no code changes |

No overnight work. Nothing merges after 23:00.

## Scope

The test for every request: does it show in the 5-minute demo? If not, it waits.

| Tier | What | Rule |
|---|---|---|
| **Must** | Anna's flow: request → cited Verdict → plain-language explanation from templates → drafted reply. Privacy Gate, Check Record, minimal Rule Card validation screen, replay mode | Runs on fixtures by Mon 2 Nov; in the bank's environment by 13:00 day 1 |
| **Should**, in this order | Tom's reverse lookup (the closing beat), form checklist, "what would make her eligible?" (thin Q&A: re-run the engine, no chat) | Started Tue 3 Nov, done by the 11:00 freeze on day 2 |
| **Cut** | Advisory lane, free-text Q&A, LLM polish of the explanation | Production slide only |

Replay mode moved up to Must: with one builder, the demo must survive a Foundry outage without anyone debugging on stage. Three of the four organiser capabilities (explanation, form, questions) appear on stage. Deadline alerts are out of scope, agreed with the sponsor on 2026-10-15 ([#24](https://github.com/smnajid/fund-gate/issues/24)).

## Seats

Each specialist owns content the demo cannot run without, and nobody else writes it. Mohamed turns it into code, fixtures or templates.

| Seat | Owns | Plays on stage |
|---|---|---|
| **Mohamed**, builder | Every module ([architecture](architecture.md#ownership)), the executable specification in `specs/`, all merges, the production slide | Drives the demo |
| **Frederic**, Banking Relation Specialist | The Requester's voice: the request emails in real RM wording, whether the plain-language explanation can be forwarded to a client as written, the reply's tone | The relationship manager who sends Anna's request |
| **Cynthia**, Fund Custodian Bank Specialist | Rule Card validation (first pass, before Samuel or Tania confirm), Fund Registration facts, form checklist content: the declarations and documents a subscription needs | The Rule Card Validator; answers the jury on fund rules |
| **Mathilde**, Data and Reporting Specialist | The demo data: Client Profile fixtures from the [demo dataset](demo-dataset.md), mapping the CRM and FMD fields onto the [contracts](contracts/) (the `?` cells), running the 26-row answer key by hand in the app as acceptance tester | Answers the jury on data and audit |
| **Xavier**, Business Development Manager | The pitch, the business case (the desk's minutes per request, [#14](https://github.com/smnajid/fund-gate/issues/14)), the roadmap slides, jury Q&A prep for the business half of the jury, timekeeping at rehearsals | Opens and closes the presentation |

The questions go to Samuel and Tania, never to a guess. Cynthia hosts their daily slot.

## What each specialist could add

Put to the team at the briefing. Each idea is held to the 5-minute test; what fails it goes on the production slide, credited to its owner.

- **Frederic**: the RM checks eligibility before emailing the desk, from the same endpoint. Production slide unless it is the structured-request beat already in the demo ([order flow](order-flow-integration.md)).
- **Cynthia**: cut-off times and the transfer agent's view of a subscription. Deadline alerts stay out of scope ([#24](https://github.com/smnajid/fund-gate/issues/24)); she judges whether a cut-off belongs in the form checklist instead.
- **Mathilde**: a report over the Check Records (Verdicts per fund, the reasons behind each Needs Review) for compliance and the desk's management. One query and one table could make it a Should item after the reverse lookup.
- **Xavier**: the 10,000 subscriptions a year turned into hours saved, once Samuel and Tania give the minutes per request. Never invented.

## The desk's 30 minutes

Samuel and Tania give 30 minutes a day, before and during the event.

- **One queue.** Questions go into [Questions for the desk](https://github.com/smnajid/fund-gate/issues?q=label%3Aask-desk) (label `ask-desk`). Teammates without GitHub send theirs to Cynthia, who files them.
- **Three questions per slot**, the ones that block the build first. Cynthia picks them the evening before.
- **Answers go into the repo the same day**: a closed question, a glossary or contract edit, an `@assumed` tag removed from `specs/`.

Already queued: does FMD carry a fund id, a document id, a source URL, investor types per registration, a status or date per registration ([contracts](contracts/)); how the desk answers a request today, with minutes per request ([#14](https://github.com/smnajid/fund-gate/issues/14)); accepting `specs/01-check-eligibility.feature` and the answer key ([#47](https://github.com/smnajid/fund-gate/issues/47)); whether Target Market is checked at mandate or client level for discretionary mandates.

## Before the event

| Day | Mohamed builds | The team | Desk slot |
|---|---|---|---|
| Tue 27 Oct | Executable specification ([#47](https://github.com/smnajid/fund-gate/issues/47)); this plan | Invited to the briefing; read the pitch | Accept the answer key, start |
| Wed 28 Oct | Skeleton ([#29](https://github.com/smnajid/fund-gate/issues/29)): Spring Boot 4 with the `pipeline` profile, Angular shell, springdoc + generated client, Cucumber on `specs/`, a stub Verdict for Anna. **Briefing**, 18:00, 60 min, remote | Briefing: demo story, seats, this plan, "what would you add?" | FMD fields (Mathilde) |
| Thu 29 Oct | Rule Engine and Scale Mapping test-first until `@answer-key` passes. Rule Card pipeline ported from the [Java spike](https://github.com/smnajid/fund-gate/tree/prototype/java-rule-card-extraction), run on the 4 funds | Cynthia validates the Draft cards (exported as a table). Frederic rewrites the emails. Mathilde fills the contracts' `?` cells | How the desk answers today, minutes per request (Xavier) |
| Fri 30 Oct | Privacy Gate, LLM egress client, Request Parser, Reply Drafter, Check Record | Cynthia: form checklist content. Frederic: template wording. Xavier: pitch v1 | Contested rules from Cynthia's validation |
| Sat 31 Oct – Sun 1 Nov | Buffer. Nothing planned depends on it | — | — |
| Mon 2 Nov | Dealer screen, validation screen, replay mode. **Anna end to end on fixtures** | 17:00 screen-share: first look at the app. Mathilde runs the answer key in it | Walk through Anna's flow |
| Tue 3 Nov | Reverse lookup. Backup video v0. **Dry-run rehearsal** at 17:00. Pre-event freeze at 19:00 | Dry run with every presenter; Xavier keeps time | Play the jury at the dry run |

Also before the event, if time allows: [/pre-merge](https://github.com/smnajid/fund-gate/issues/28) and the [reviewer-agent practice](https://github.com/smnajid/fund-gate/issues/27). Both are worth less with one builder; the reviewer agents still run before each merge.

## Day 1 (Wed 4 Nov)

| Time | Mohamed | The team | Notes |
|---|---|---|---|
| 9:00–9:30 | **Kickoff**: what changed overnight, today's plan | Same | The plan is already known; 10 min of challenge |
| 9:30–13:00 | Clone into the bank's internal environment. Bank adapters, one port at a time: FMD for Share Classes and Fund Registrations, CRM for Client Profiles; each port falls back to its fixture adapter on its own | Mathilde pairs on the field mapping. Cynthia, with Samuel or Tania, validates the cards of any fund the bank adds. Frederic and Xavier: pitch v2 | |
| **13:00** | **Checkpoint 1**, lunch together | | |
| 13:00–18:00 | Should tier, in order. Fix what Mathilde's answer-key run finds | Mathilde reruns the answer key in the bank's environment. Cynthia and Frederic review every Verdict sentence and reply | |
| **18:00** | **Checkpoint 2**, backup video, dinner | | |
| 18:00–21:30 | Should tier | **Rehearsal 1 at 20:00** | Earlier than with three builders: the presenters are free from noon |
| **21:30** | **Checkpoint 3**: pick the Should items for day 2 | | |
| 22:00 | Leave | | |

## Day 2 (Thu 5 Nov)

| Time | Everyone | Notes |
|---|---|---|
| 8:00 | 15-min sync | |
| 8:00–11:00 | Mohamed finishes the picked Should items. The team rehearses the handovers and the jury Q&A | |
| **11:00** | **Feature freeze** | Unfinished work is hidden, not rushed. Bug fixes only |
| 11:00–15:00 | Harden. `demo-flow-verifier` on `/loop 10m`. Rehearsals at **12:00, 13:30, 14:30**, each followed by a 10-min fix list | Final backup video after the 13:30 rehearsal |
| 15:00 | Code stops | |
| 15:00–16:00 | Finalise the presentation | |

## Checkpoints and cut triggers

Each checkpoint is a 5-minute standing sync: done, next, blocked.

| Checkpoint | Done means | If not |
|---|---|---|
| Thu 29 Oct, evening | `@answer-key` passes; Draft cards for the 4 funds with Cynthia | Seed the cards from the spike output; Cynthia validates those |
| Mon 2 Nov, evening | Anna's flow runs end to end on fixtures | Validation screen becomes a read-only JSON view; reverse lookup moves to day 1 |
| Tue 3 Nov, 19:00 | Backup video v0, dry run done | Record the video before anything else on day 1 |
| 10:30 day 1 | The app boots in the bank's environment; Foundry answers | Switch to our own Anthropic key; keep every port on fixtures |
| 13:00 day 1 | Anna's flow in the bank's environment, fixture adapters allowed | The demo runs on fixtures; bank data becomes a slide |
| 18:00 day 1 | Must tier on validated Rule Cards; backup video recorded | Drop the Should items after the reverse lookup |
| 21:30 day 1 | Should items picked for day 2 | Drop the one furthest from done |
| 11:00 day 2 | Feature freeze | Hide what isn't done |

## Merging

- **Mohamed merges everything.** He works on short-lived branches, in parallel worktrees when agents build two modules at once (`claude --worktree <module>`), and merges to `main` after the reviewer agents report nothing.
- **A specialist who tries an AI agent** changes only content files: template wording, checklist content, fixtures, the examples in `specs/`. On a branch, by pull request; Mohamed reviews and merges.
- **From the pre-event freeze on, `main` is the demo.** `demo-flow-verifier` (which runs the `@must` scenarios) passes before every merge. A merge that breaks the demo is reverted on the spot, never debugged on `main`.

## Reviewer agents

All live in `.claude/agents/` and load when the folder is trusted. They report; they never edit. Ask by name, for example: *"use the privacy-gate-auditor on my branch's diff against main"*.

| Agent | Checks | When |
|---|---|---|
| `java-reviewer` | Spring Boot layering, security, tests | Any Java change |
| `typescript-reviewer` | Angular types, async, templates | Any Angular change |
| `privacy-gate-auditor` | Client data that could reach the LLM (ADR 0004) | Intake, Privacy Gate, LLM client, Request Parser, Reply Drafter, Check Record; before every rehearsal |
| `engine-purity-reviewer` | A Verdict that isn't deterministic or moves toward Eligible without a validated rule (ADR 0002, 0007) | Rule Engine, Scale Mapping, reverse lookup |
| `demo-flow-verifier` | The demo story against the running app | Checkpoints, freeze, rehearsals |

## One builder is a single point of failure

- **Replay mode** (Must): one flag serves the Request Parser and Reply Drafter answers recorded at the last rehearsal. The engine still runs live, so the Verdicts stay real.
- **Backup video**: first version on Tue 3 Nov, refreshed at 18:00 day 1 and after the 13:30 rehearsal.
- **Anyone can start the demo**: one documented command on the presenting laptop, practised by Xavier and Frederic at the dry run.

## Definition of done

The demo is done when all of these hold:

- The Must tier runs cleanly under `demo-flow-verifier`.
- Every `@answer-key` row and every `@must` scenario in `specs/` passes.
- `privacy-gate-auditor` finds nothing.
- A backup video is recorded.
- Two rehearsals finish under 5 minutes.

A Should item counts only when it passes the same checks.

## Rehearsals

A rehearsal is the full presentation as it will happen on stage: same presenters and order, the live app on the presenting laptop with the demo data, the slides, a stopwatch against 5 minutes, and someone playing the jury who asks 2–3 hard questions afterwards. It catches what tests don't: overrunning, confusing click paths, a silent wait on an LLM call, the projector, the handovers.
