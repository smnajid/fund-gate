# Hackathon kit: install at hour 0

The Claude Code setup every teammate runs in the 36 hours. Why each piece is here: [ai-tooling.md](../playbook/ai-tooling.md). Target time: 10 minutes.

## 1. Point Claude Code at Foundry

Put these in your shell profile, not in the repo. Deployment names come from the event's Foundry resource.

```bash
export CLAUDE_CODE_USE_FOUNDRY=1
export ANTHROPIC_FOUNDRY_RESOURCE=<resource-name>       # name only, not a URL
export ANTHROPIC_FOUNDRY_API_KEY=<key>                  # or leave unset and run `az login`
export ANTHROPIC_DEFAULT_OPUS_MODEL=<opus-5.5 deployment name>
export ANTHROPIC_DEFAULT_SONNET_MODEL=<sonnet-5.5 deployment name>
export ANTHROPIC_DEFAULT_HAIKU_MODEL=<haiku deployment name>
```

- Every deployment must use the **Hosted on Azure** option. "Hosted on Anthropic" sends code outside the tenant.
- Pin all three models. Unpinned, `opus` resolves to an older model, and a model that isn't deployed fails on the first request.
- Check: start `claude`, run `/status`, and look for `Microsoft Foundry`.

Fallback when Foundry is down or out of quota: unset `CLAUDE_CODE_USE_FOUNDRY` and use your own Anthropic key. The data rules stay the same.

## 2. Clone and trust the repo

```bash
git clone https://github.com/smnajid/fund-gate && cd fund-gate
claude          # accept the trust prompt
```

Trusting the folder loads `.claude/settings.json`:
- the plugin marketplaces and the team plugins (`frontend-design`, `pr-review-toolkit`);
- the data guard (deny rules plus the hook in `.claude/hooks/data-guard.py`, which needs `python3`);
- the project subagents in `.claude/agents/` and the skills in `.claude/skills/`.

Check: `/plugin` lists both plugins as enabled; `/agents` lists `privacy-gate-auditor`, `engine-purity-reviewer`, `demo-flow-verifier` and the stack reviewers; typing `/` shows `blast-radius`, `create-verification-skill` and `unslop`.

## 3. Opt into what your role needs

Personal choices go in `.claude/settings.local.json`, which is git-ignored:

```json
{
  "enabledPlugins": {
    "playwright@claude-plugins-official": true,
    "document-skills@anthropic-agent-skills": true,
    "jdtls-lsp@claude-plugins-official": true
  }
}
```

- `playwright`: Person 3 (UIs) and Person 5 (demo verifier).
- `document-skills` (`pptx`): Person 5 (pitch deck).
- `jdtls-lsp` or `typescript-lsp`: everyone, once the stack is chosen.
- `security-guidance`: Person 4 if wanted, for the Privacy Gate. It reviews every turn, which slows you down.

## 4. Once the stack is chosen

- Delete the reviewer and build-resolver pair we don't use (`java-*` or `typescript-reviewer` and `ts-build-resolver`) from `.claude/agents/`.
- Add the LSP plugin to `enabledPlugins` in `.claude/settings.json` for the whole team.

## 5. The bank's client export

- It goes in `data/bank/` only (git-ignored). Better: a folder on a machine with no Claude session.
- A human converts it into the canonical Client Profile shape. No agent reads it, including through scripts, MCP servers or copy-paste.
- If an agent hits `Blocked by data-guard`, that is the guard working. Point the agent at the schemas and the synthetic fixtures instead.

Strict mode, for whoever sits closest to the export: also turn on the OS sandbox in `.claude/settings.local.json`. It stops scripts and `grep -r` from reading the folder too. Expect prompts for Docker and package downloads.

```json
{
  "sandbox": {
    "enabled": true,
    "allowUnsandboxedCommands": false,
    "failIfUnavailable": true,
    "filesystem": { "denyRead": ["./data/bank"] }
  }
}
```

## 6. Daily moves

| When | Do |
|---|---|
| Starting your module | `claude --worktree <module>`, one worktree per owner |
| Build broken | the `java-build-resolver` or `ts-build-resolver` agent |
| Before merging | the stack reviewer agent; `privacy-gate-auditor` if an LLM call or client field is touched; `engine-purity-reviewer` for the engine |
| Risky change during integration | `/blast-radius` |
| App boots for the first time | `/create-verification-skill`, which generates `verify-fund-gate` |
| Integration checkpoint, feature freeze, rehearsal | the `demo-flow-verifier` agent; `/loop 10m` it in the last hours |
| Writing pitch or README text | `/unslop` |
