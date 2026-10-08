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
- the plugin marketplaces and the team plugins (`frontend-design`, `pr-review-toolkit`, `jdtls-lsp`, `typescript-lsp`);
- the project subagents in `.claude/agents/` and the skills in `.claude/skills/`, including the official `angular-developer` skill.

The language plugins need their servers on your `PATH`: `brew install jdtls` (JDK 17+) and `npm install -g typescript-language-server typescript`.

Check: `/plugin` lists both plugins as enabled; `/agents` lists `privacy-gate-auditor`, `engine-purity-reviewer`, `demo-flow-verifier` and the stack reviewers; typing `/` shows `blast-radius`, `create-verification-skill` and `unslop`.

## 3. Opt into what your role needs

Personal choices go in `.claude/settings.local.json`, which is git-ignored:

```json
{
  "enabledPlugins": {
    "playwright@claude-plugins-official": true,
    "document-skills@anthropic-agent-skills": true,
    "context7@claude-plugins-official": true
  }
}
```

- `playwright`: Person 3 (UIs) and Person 5 (demo verifier).
- `document-skills` (`pptx`): Person 5 (pitch deck).
- `context7`: anyone who keeps hitting outdated framework APIs.
- `security-guidance`: Person 4 if wanted, for the Privacy Gate. It reviews every turn, which slows you down.

## 4. Angular CLI MCP (Person 3)

Gives the agent `ng` build, test and serve targets, Angular docs search and the best-practices guide:

```bash
claude mcp add angular-cli -- npx -y @angular/cli mcp
```

## 5. Daily moves

| When | Do |
|---|---|
| Starting your module | `claude --worktree <module>`, one worktree per owner |
| Build broken | `java-build-resolver` (Spring Boot) or `ts-build-resolver` (Angular) |
| Before merging | the stack reviewer agent; `privacy-gate-auditor` if an LLM call or client field is touched; `engine-purity-reviewer` for the engine |
| Risky change during integration | `/blast-radius` |
| App boots for the first time | `/create-verification-skill`, which generates `verify-fund-gate` |
| Integration checkpoint, feature freeze, rehearsal | the `demo-flow-verifier` agent; `/loop 10m` it in the last hours |
| Writing pitch or README text | `/unslop` |
