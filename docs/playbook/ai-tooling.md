# AI tooling for the 36 hours

Resolves [Which AI tools make the team faster during the 36 hours?](https://github.com/smnajid/fund-gate/issues/18).

The team uses **Claude Code**, served from **Microsoft Foundry**. This page says what we bring, what each tool is for, and what we left out. The kit itself is committed in `.claude/` and `.agents/skills/`. Installing it takes about 10 minutes at hour 0: see [hackathon-kit.md](../agents/hackathon-kit.md). The evidence behind every claim is in two research notes: [Claude Code, plugins, Foundry](../research/ai-tools-claude-code.md) and [ECC, pstack, Cursor plugins](../research/ai-tools-ecc-pstack.md).

## What decided it

- **Coding agents are an LLM egress too.** Anything Claude Code reads goes to the model. The organisers forbid client-identifying data reaching an external LLM, so the bank's hour-0 client export is off-limits to every agent, not only to our product (ADR 0004).
- **Foundry, "Hosted on Azure" deployments only.** With this option, prompts and completions stay in Azure. "Hosted on Anthropic" deployments, and every Fable model, run on Anthropic's infrastructure. Foundry turns off telemetry by default.
- **Foundry costs some features.** Claude in Chrome, Artifacts (so no Slides artifact), cloud sessions, `/schedule` and Routines are unavailable. Subagents, skills, plugins, hooks, MCP, worktrees, `/loop` and the sandbox all work.
- **Context is the scarce resource, not money.** Budget is unlimited, but every always-loaded rule and MCP tool schema costs every turn, for 5 people, for 36 hours. MCP tool search is unsupported on Azure-hosted deployments, so each enabled MCP server's full tool list sits in context.
- **We already have a good base.** About 37 Matt Pocock skills: `tdd`, `prototype`, `diagnosing-bugs`, `codebase-design`, `code-review`, `to-tickets`, `handoff` and others. A new tool has to add something these don't.

## The kit

| What | Where | Who uses it, when |
|---|---|---|
| **Data guard**: `permissions.deny` on `data/bank/**` plus a PreToolUse hook that blocks any tool call naming that folder | `.claude/settings.json`, `.claude/hooks/data-guard.py` | Everyone, always on |
| **privacy-gate-auditor** subagent: one egress, gate-only input, fail closed, allow-list, logged (ADR 0004/0005) | `.claude/agents/` | Person 4 before each merge touching an LLM call, everyone before rehearsal |
| **engine-purity-reviewer** subagent: no LLM, deterministic, Draft never decides, downgrade-only (ADR 0002/0003/0005) | `.claude/agents/` | Person 2 on every Rule Engine, Scale Mapping or reverse-lookup change |
| **demo-flow-verifier** subagent: runs the coverage set and the hero flow, times each step | `.claude/agents/` | Person 5 at each integration checkpoint, after feature freeze, before each rehearsal |
| **java-reviewer / java-build-resolver** or **typescript-reviewer / ts-build-resolver** (from ECC, trimmed). Delete the pair we don't use once the stack is chosen | `.claude/agents/` | Everyone: the resolver when the build breaks, the reviewer before merging |
| **create-verification-skill** (ported from pstack): generates a `verify-fund-gate` skill that launches, checks and drives the real app, with a feature map | `.agents/skills/` | Person 5 once the app boots (around hour 6 to 10). Every agent uses the generated skill afterwards |
| **blast-radius** (ported from pstack): finds the one fact a change is safe because of, and proves it by running code | `.agents/skills/` | Before merging Rule Engine or Privacy Gate changes during integration |
| **unslop** (pstack): strips AI tells from prose | `.agents/skills/` | Pitch text, README, reply templates |
| **frontend-design** plugin: design direction so the screens don't look like a stock AI dashboard | `enabledPlugins` | Person 3 |
| **pr-review-toolkit** plugin: six focused review agents (silent failures, test gaps, type design, comments) | `enabledPlugins` | Everyone on PRs |
| **playwright** plugin (MCP): Claude opens and checks its own screens through accessibility snapshots | opt-in per person | Person 3, and Person 5 for the demo verifier |
| **document-skills** plugin (`pptx`): the pitch deck on the bank's template | opt-in per person | Person 5 |
| **LSP plugin** (`jdtls-lsp` or `typescript-lsp`) | opt-in, once the stack is chosen | Everyone |

Playwright and `document-skills` are opt-in because MCP tool schemas and large skills cost every person context on every turn. The other three people don't need them.

## Who uses what

| Person | Owns (architecture.md) | Main tools |
|---|---|---|
| 1 | Rule Card pipeline, Share Class catalog | `tdd`, the stack resolver agent, `blast-radius` on the quote-on-page check |
| 2 | Rule Engine, Scale Mapping, reverse lookup | `tdd` against the coverage set, `engine-purity-reviewer`, `blast-radius` |
| 3 | Dealer UI, Validation UI | `prototype` (before the event, issue #17), `frontend-design`, Playwright |
| 4 | Intake, Privacy Gate, egress, Parser, Reply Drafter, advisory lane | `privacy-gate-auditor` on every merge, `tdd` for the gate's fail-closed cases, `claude-api` skill for Foundry calls |
| 5 | Adapters, Check Record, compose, pitch | `demo-flow-verifier`, `create-verification-skill`, `pptx`, `unslop` |

## How we work with agents in the 36 hours

- **One worktree per module owner**: `claude --worktree <module>`. `.claude/worktrees/` is already git-ignored. Never add `data/bank/` to `.worktreeinclude`.
- **Background agents for slow loops**: `/bg` a session that runs the coverage set or a long build. `/loop 10m` to rerun the demo-flow verifier during the last hours.
- **Subagents for review, not for writing the core**: the Rule Engine and Privacy Gate are written test-first by their owner. Reviewers and verifiers report and never edit.
- **Pin the models** in each person's environment (`ANTHROPIC_DEFAULT_OPUS_MODEL` and the Sonnet and Haiku equivalents). Otherwise the `opus` alias on Foundry resolves to an older model, or to one that isn't deployed.

## Privacy rules for coding agents

1. The bank's client export goes into `data/bank/` only, which is git-ignored. Better still, it goes to a machine or folder no Claude session can reach. A human converts it into the canonical Client Profile shape, and only pseudonymised or synthetic data enters the repo.
2. The data guard is a seatbelt against accidents, not a wall. It stops Read, Edit, Grep, Glob, `@` mentions, `cat`-style Bash and any command naming the folder. It does not stop a script that builds the path at runtime, an MCP server, or a human pasting data in. For a stronger OS-level block, turn on the sandbox (see the kit guide), at the cost of friction with Docker and package downloads.
3. No docs MCPs that store queries (Context7) on a machine that holds client data. The Microsoft Learn MCP is public docs only and fine.
4. A Foundry deployment must be "Hosted on Azure", Opus 5.5 or Sonnet 5.5. No Fable on the event subscription.
5. Demo, pitch and screenshots use synthetic data from [demo-dataset.md](demo-dataset.md) only.

## Considered and left out

| Candidate | Verdict | Why |
|---|---|---|
| **ECC full install** (`--profile minimal` or `core`) | Skip | Even "minimal" writes about 400 files into `~/.claude`: 68 agents, 94 commands that clash with our `pr` and `code-review`, and 122 rules adding roughly 18k tokens to every turn (our estimate). Its rules impose 80% coverage and "always immutable" code. The `core` hooks block the first edit of each file and re-send the transcript to a model every 50 messages. We copied 4 agents instead. |
| ECC `nutrient-document-processing` | Never | Sends documents to an outside API. |
| **pstack as a whole** (`poteto-mode`, `arena`, `architect`, principles) | Skip | Built for Cursor: custom modes, Grok routing, Cursor's question tool. `architect` overlaps `codebase-design` and `prototype`. We ported the three skills that work anywhere. |
| pstack `interrogate` (multi-model adversarial review) | Later, if someone owns it | Needs its reviewer step rewritten for the Agent tool. `pr-review-toolkit` covers most of it. |
| Cursor Orchestrate, Advisor, Ralph Loop, Figma-to-UI | Skip | Cursor-only, or an autonomous loop we can't supervise in 36 hours, or a Figma step we won't have. |
| `security-guidance` plugin | Optional | Runs an LLM review of the diff at the end of every turn. That is useful, but slow for 5 people iterating fast. Person 4 may turn it on for the Privacy Gate. |
| `feature-dev`, `hookify`, `code-review` plugins | Skip | Overlap our `implement`, `to-tickets`, `code-review` skills and the hand-written data guard. |
| Context7 | Skip | Stores model-written queries. |
| Claude in Chrome, Slides artifact | Not available | Not available on Foundry. Use Playwright and `pptx`. |
| Figma MCP | Skip | Nobody designs in Figma first in 36 hours. Use `prototype` before the event instead. |

## Validation (2026-10-08)

- **Hook, unit level:** 14 cases fed to `data-guard.py`. All 9 that should block did: relative, absolute and `../` paths, `cat`, `cd`, Grep path, Glob pattern, cwd inside the folder, MCP input. All 5 that should pass did: a fixture read, a doc edit mentioning the folder, a Grep for the text, an unrelated command, bad JSON.
- **Hook, live:** as soon as `.claude/settings.json` existed, the authoring session itself was blocked when it tried to plant a probe file in the folder. A headless probe of all four routes (Read, `cat`, a Python script, Grep) is in the PR description. Script reads are expected to get through unless the sandbox is on.
- **Subagents:** a headless session in a throwaway copy of the kit found both auditors by name. On a planted fixture they flagged every violation: the engine imports the SDK and asks the model for the Target Market, the engine never checks Draft cards or the regime, the parser sends the client name ungated, and a second SDK client sits outside the egress.
- **Not done:** a timed with-and-without comparison on a real Rule Engine slice. Product code is not allowed before the event, so the first real measurement is the hour-0 rehearsal.

## Open questions

- **Organisers**: are agent configs, plugins and skills allowed? They are tooling, not product code. Asked on issue #18.
- **Event subscription**: can it deploy Claude "Hosted on Azure" with enough tokens per minute for 5 coding agents plus the product? See the Foundry note on the map.
- **Pinning**: `claude-plugins-official` has no release tags. We set `autoUpdate: false`; to fully freeze, fork it and pin `ref` a week before the event.
