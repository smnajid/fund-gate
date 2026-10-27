# FundEli5: how we win in 20 hours

<!-- The one slide for the 9:00 kickoff. Detail: team-plan.md. -->

**Goal:** a 5-minute demo where a dealer gets a cited, auditable Verdict for Anna in seconds, explained so her RM can forward it.
**Rule:** if it doesn't show in the 5 minutes, it waits.
**Where we start:** the Must tier already runs on fixtures. Today moves it onto the bank's data, adds the Should tier, and rehearses.

| Must (bank's environment by 13:00) | Should (by 11:00 tomorrow) | Cut |
|---|---|---|
| Anna: request → cited Verdict → plain explanation → reply · Privacy Gate · Check Record · validation screen · replay mode | Tom's reverse lookup · form checklist · "what would make her eligible?" | Advisory lane · free-text Q&A · LLM polish |

| Seat | Owns |
|---|---|
| Mohamed | All the code, bank adapters, merges, demo driver |
| Frederic | Request emails, plain-language wording, plays the RM |
| Cynthia | Rule Card validation, form checklist content, hosts the desk slot |
| Mathilde | Field mapping with Mohamed, answer key run in the app |
| Xavier | Pitch, business case, timekeeping, opens and closes |

**Desk:** Samuel and Tania, 30 min a day; questions go to Cynthia, never a guess.
**Checkpoints:** 10:30 app boots in the bank's environment · 13:00 Anna on bank data · 18:00 Must tier + backup video · 20:00 rehearsal 1 · 21:30 pick tomorrow's Should · 22:00 go home
**Tomorrow:** 8:00 sync · 11:00 **freeze** · rehearsals 12:00 / 13:30 / 14:30 · 15:00 code stops

**Merging:** only Mohamed merges; content changes from others come by pull request; what breaks the demo is reverted.
