## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues through the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the five default labels: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Hackathon kit

Before merging, run the `privacy-gate-auditor` agent on changes touching an LLM call or client field, and `engine-purity-reviewer` on Rule Engine changes. Setup, plugins and daily moves: `docs/agents/hackathon-kit.md`.
