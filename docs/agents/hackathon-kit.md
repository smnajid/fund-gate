# Hackathon kit: install before you build

The Claude Code setup the builder runs from the first day of the pre-build, and any specialist who tries an AI agent installs too ([team-plan.md](../playbook/team-plan.md)). Why each piece is here: [ai-tooling.md](../playbook/ai-tooling.md). Target time: 10 minutes.

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

- `playwright`: the builder, for the screens and the demo verifier.
- `document-skills` (`pptx`): whoever builds the pitch deck.
- `context7`: anyone who keeps hitting outdated framework APIs.
- `security-guidance`: the builder if wanted, for the Privacy Gate. It reviews every turn, which slows you down.

## 4. Angular CLI MCP

Gives the agent `ng` build, test and serve targets, Angular docs search and the best-practices guide:

```bash
claude mcp add angular-cli -- npx -y @angular/cli mcp
```

## 5. Stack practices (set up with the skeleton)

The POC runs on Spring Boot 4 and Angular so that the team checks generated code in the language it knows best ([ADR 0006](../adr/0006-spring-boot-and-angular-python-only-for-real-added-value.md)). Three practices recover most of the speed of a single-language stack. Put them in the scaffold before anyone builds a feature.

1. **Generated client types.** Add `springdoc-openapi` to the backend so it serves its OpenAPI spec. Generate the Angular types and HTTP client from that spec with `openapi-generator` (`typescript-angular`), and put the generate step in an npm script. Regenerate after every API change instead of editing DTOs by hand. Generated files are never edited.
2. **One codebase.** The Rule Card pipeline is a command-line mode of the same Spring Boot app, for example a `pipeline` profile with an `ApplicationRunner`, not a second project. That gives one build, one image and shared domain types. It runs as its own process, with no Client Profile or CRM adapter wired in, so the offline and online runtimes stay separate (ADR 0004).
3. **Fast loop, tests as the safety net.** Turn on `spring-boot-devtools` so a backend change restarts in seconds. The Rule Engine is built test-first (`/tdd`) against the scenarios of the executable specification in `specs/` (section 6). A wrong Verdict then fails a scenario even when a review misses it.

Python only if a module needs something the JVM can't do well. Today none does.

## 6. Run the executable specification

`specs/` describes what FundEli5 does in Gherkin: start with `specs/00-fundeli5.feature`. The feature files are written before the event; the step definitions are written with each module, from Wed 28 Oct. `specs/` never moves.

**Cucumber is wired into the skeleton on Wed 28 Oct**, before any feature is built:
- `cucumber-java`, `cucumber-spring` and `cucumber-junit-platform-engine` with a `@Suite` runner, `SpecificationSuite`, that selects `@SelectDirectories("../specs")` (relative to `backend/`, where Maven runs). Not the `cucumber.features` property: under the JUnit 6 Suite engine it finds nothing.
- Cucumber reports an undefined step as an error, so the suite stays out of the default build. `./mvnw test` runs `SpecificationRunsTest` instead, which runs the whole specification and fails only on a scenario that really fails or errors. To see a module's scenarios one by one, run the suite by name: `./mvnw test -Dtest=SpecificationSuite -Dcucumber.filter.tags="<tags>"`.
- Default filter `cucumber.filter.tags=not @live-llm`, and the LLM egress client in replay mode, so a run makes no model call. A run with `-Dcucumber.filter.tags=@live-llm` uses the real model.
- One step-definition package per module. An undefined step is that module's to-do list.

Who runs it:

| Who | Runs | When |
|---|---|---|
| Mohamed, Rule Engine | `./mvnw test -Dtest=SpecificationSuite -Dcucumber.filter.tags="@rule-engine"` in `backend/`, in the `/tdd` loop: pick the next failing example, make it pass, repeat. `@answer-key` once the engine decides every rule | Every change to the Rule Engine, Scale Mapping, reverse lookup |
| Mohamed, other modules | the module's tags (see `00-fundeli5.feature`), e.g. `@privacy-gate or @check-record` | Before merging the module |
| `/pre-merge` | the module tags of the changed files, plus `@answer-key` | Before every merge |
| `demo-flow-verifier` | `@must`, then `@should` once picked, against the running app | Checkpoints, freeze, rehearsals |
| Cynthia, then the desk | Cynthia reads the `@assumed` scenarios; Samuel or Tania confirm them in the daily slot ([#50](https://github.com/smnajid/fund-gate/issues/50)); Mohamed removes each tag once confirmed | Before the event; again after mapping the bank's data |

`@assumed` scenarios still run. A failing `@answer-key` row blocks the rehearsal.

## 7. Daily moves

| When | Do |
|---|---|
| Building two modules at once | `claude --worktree <module>`, one worktree per agent |
| API changed | regenerate the Angular client from the OpenAPI spec, commit both sides together |
| Build broken | `java-build-resolver` (Spring Boot) or `ts-build-resolver` (Angular) |
| Before merging | `/pre-merge` (runs your modules' scenarios); the stack reviewer agent; `privacy-gate-auditor` if an LLM call or client field is touched; `engine-purity-reviewer` for the engine |
| Risky change during integration | `/blast-radius` |
| App boots for the first time | `/create-verification-skill`, which generates `verify-fund-gate` |
| Integration checkpoint, feature freeze, rehearsal | the `demo-flow-verifier` agent; `/loop 10m` it in the last hours |
| Writing pitch or README text | `/unslop` |
