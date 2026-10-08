## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues through the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the five default labels: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Hackathon kit

Client data never reaches an agent: `data/bank/` holds the bank's client export and is blocked by `.claude/hooks/data-guard.py`. If a tool call is blocked, work from the canonical schemas and synthetic fixtures; never route around the guard. Before merging, run `privacy-gate-auditor` on changes touching an LLM call or client field, and `engine-purity-reviewer` on Rule Engine changes. Setup and daily moves: `docs/agents/hackathon-kit.md`.
