# Team plan: 20 build hours

Resolves [How does a team of 5 run the 20 build hours?](https://github.com/smnajid/fund-gate/issues/11). The one-slide version for the 9:00 kickoff is [kickoff-slide.md](kickoff-slide.md).

## The window

| Day | Build | Then |
|---|---|---|
| Day 1 | 9:00–22:00 (13 h) | everyone goes home and sleeps |
| Day 2 | 8:00–15:00 (7 h) | 15:00–16:00: finalise the presentation, no code changes |

That is about 20 hours of building, not 36. No overnight work. A builder who codes at home anyway does so at their own risk and merges nothing after 23:00.

## Scope

The test for every request: does it show in the 5-minute demo? If not, it waits.

| Tier | What | Rule |
|---|---|---|
| **Must** | Anna's flow: request → cited Verdict → plain-language explanation from templates → drafted reply. Privacy Gate, Check Record, minimal Rule Card validation screen. | Done by 18:00 day 1 |
| **Should** | Form checklist, Tom's reverse lookup, "what would make her eligible?" (thin Q&A: re-run the engine, no chat) | Picked at 21:30 day 1, done by the 11:00 freeze |
| **Cut** | Advisory lane, free-text Q&A, LLM polish of the explanation | Production slide only |

Three of the four organiser capabilities (explanation, form, questions) appear on stage. Deadline alerts are out of scope, agreed with the sponsor on 2026-10-15 ([#24](https://github.com/smnajid/fund-gate/issues/24)).

## Seats

| Seat | Owns |
|---|---|
| Builder 1 | Rule Card pipeline, Share Class catalog, seeding the 4 funds, adapters and Intake (maps the bank's data with the desk member at 9:30) |
| Builder 2 | Rule Engine, Scale Mapping, reverse lookup, plain-language templates, form checklist logic, "what would make her eligible?"; test-first against the coverage set |
| Builder 3 | Dealer screen, validation screen, LLM egress client, Request Parser, Reply Drafter, replay mode |
| Lead (Mohamed) | Skeleton app, Privacy Gate, Check Record, all merges after 18:00 day 1, pitch and production slide |
| Desk member | Bank data mapping, Rule Card validation, template and checklist wording, first answer to any domain question, co-presents the problem |

Builders ask the desk member before guessing a domain answer.

## Before the event

1. **Practice the reviewer agents** with the lead: [Do the reviewer agents catch a planted privacy leak and an impure Verdict?](https://github.com/smnajid/fund-gate/issues/27).
2. **Build `/pre-merge`**: [Build the /pre-merge skill](https://github.com/smnajid/fund-gate/issues/28).
3. **Practise the skeleton** in a throwaway repo; save the prompts and commands in the playbook, not the code: [What prompts build the skeleton app in 60 minutes?](https://github.com/smnajid/fund-gate/issues/29).
4. **Team briefing**, about a week before, 45–60 min, remote is fine. Send the pitch and this plan two days ahead. Agenda: demo story, seats, this plan, the reviewer-agent practice run replayed in 5 minutes. Everyone installs the kit except Foundry, which needs the event subscription.
5. Print this page.

## Day 1

| Time | Everyone | Notes |
|---|---|---|
| 9:00–9:30 | **Kickoff**: demo story, seats, schedule, scope tiers; 10 min of challenge | The plan is a proposal the team owns, not a debate from scratch |
| 9:30–10:30 | Lead builds the skeleton. Builders install the kit and read their seat. Desk member + Builder 1 map the bank's data onto the coverage set | Skeleton: Spring Boot 4 with the `pipeline` profile, Angular shell, springdoc + generated client, one stub endpoint showing a fake Verdict for Anna |
| **10:30** | **Checkpoint 0** | |
| 10:30–13:00 | Build. Builder 1 runs the pipeline on the 4 funds first: extraction takes minutes and the desk needs time to validate | Desk validates Rule Cards from 11:00, as JSON until the screen exists |
| **13:00** | **Checkpoint 1**, lunch together | |
| 13:00–18:00 | Deepen to the Must tier on real data | Desk writes template and checklist wording |
| **18:00** | **Checkpoint 2**, record the backup video, dinner | Lead becomes the only merger. Lead starts the pitch |
| 18:00–21:30 | Should tier | Desk rehearses the problem statement |
| **21:30** | **Checkpoint 3**: pick the Should items for day 2 | |
| 22:00 | Leave | |

## Day 2

| Time | Everyone | Notes |
|---|---|---|
| 8:00 | 15-min sync | |
| 8:00–11:00 | Finish the picked Should items. Builder 3 builds replay mode | |
| **11:00** | **Feature freeze** | Unfinished work is hidden, not rushed. Bug fixes only |
| 11:00–15:00 | Harden. `demo-flow-verifier` on `/loop 10m`. Rehearsals at **12:00, 13:30, 14:30**, each followed by a 10-min fix list | Final backup video after the 13:30 rehearsal |
| 15:00 | Code stops | |
| 15:00–16:00 | Finalise the presentation | |

## Checkpoints and cut triggers

Each checkpoint is a 5-minute standing sync: done, next, blocked. Between checkpoints the lead walks round the seats about once an hour instead of calling meetings.

| Checkpoint | Done means | If not |
|---|---|---|
| 10:30 day 1 | Skeleton boots on every laptop; Foundry answers | Switch to our own Anthropic key |
| 13:00 day 1 | Anna's flow runs end to end, stubs allowed | Pair a second person on the blocker |
| 18:00 day 1 | Must tier on real validated Rule Cards; backup video recorded | Pipeline not producing cards: seed from the spike output, desk validates |
| 21:30 day 1 | Should items picked for day 2 | Drop the one furthest from done |
| 11:00 day 2 | Feature freeze | Hide what isn't done |

## Merging

- **Until 18:00 day 1:** each owner works in their own worktree (`claude --worktree <module>`) on a short-lived branch, runs `/pre-merge`, fixes what it reports, and merges to `main` themselves. Merge every 1–2 hours.
- **From 18:00 day 1, and at every checkpoint:** `main` is the demo. Owners say "ready"; the lead checks the scope tier, runs `demo-flow-verifier`, and merges. A merge that breaks the demo is reverted on the spot, never debugged on `main`.

## Reviewer agents

All live in `.claude/agents/` and load when the folder is trusted. They report; they never edit. `/pre-merge` picks them from the changed files. Without it, ask by name, for example: *"use the privacy-gate-auditor on my branch's diff against main"*.

| Agent | Checks | When |
|---|---|---|
| `java-reviewer` | Spring Boot layering, security, tests | Any Java change |
| `typescript-reviewer` | Angular types, async, templates | Any Angular change |
| `privacy-gate-auditor` | Client data that could reach the LLM (ADR 0004) | Intake, Privacy Gate, LLM client, Request Parser, Reply Drafter, Check Record; before every rehearsal |
| `engine-purity-reviewer` | A Verdict that isn't deterministic or moves toward Eligible without a validated rule (ADR 0002, 0007) | Rule Engine, Scale Mapping, reverse lookup |
| `demo-flow-verifier` | The demo story against the running app | Checkpoints, freeze, rehearsals |

## Demo fallback

1. **Replay mode**: one flag serves the Request Parser and Reply Drafter answers recorded at the last rehearsal. The engine still runs live, so the Verdicts stay real.
2. **Backup video**: last resort if the laptop or app fails.

## Definition of done

The demo is done when all of these hold:

- The Must tier runs cleanly under `demo-flow-verifier`.
- Every coverage-set test is green.
- `privacy-gate-auditor` finds nothing.
- A backup video is recorded.
- Two rehearsals finish under 5 minutes.

A Should item counts only when it passes the same checks.

## Rehearsals

A rehearsal is the full presentation as it will happen on stage: same presenters and order, the live app on the presenting laptop with the demo data, the slides, a stopwatch against 5 minutes, and someone playing the jury who asks 2–3 hard questions afterwards. It catches what tests don't: overrunning, confusing click paths, a silent wait on an LLM call, the projector, the handovers.
