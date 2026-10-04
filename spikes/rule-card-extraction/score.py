# /// script
# dependencies = ["anthropic", "pypdf"]
# ///
"""PROTOTYPE — scores an extract.py run against gold-rule-card.json.

  python3 score.py                 # latest run
  python3 score.py runs/<file>.json
  python3 score.py --check-gold    # verify every gold quote is on its cited page

Metrics: rule recall (required gold rules found), value accuracy (found rules with the right
parameters), citation support (extracted quotes found verbatim on the cited page, and landing on a
page the answer key cites), traps (negative rules, bonus contradiction), unexpected rules for review.
"""
import json, sys
from pathlib import Path

from extract import CLASSES, KIDS, norm, select_pages

HERE = Path(__file__).parent
GOLD = json.load(open(HERE / "gold-rule-card.json"))
PAGES = {n: json.load(open(HERE / "docs" / f"{n}.pages.json")) for n in ["prospectus", *KIDS]}


def expand(rule):
    return list(CLASSES) if rule["share_class"] == "*" else [rule["share_class"]]


def key(rule, cls):
    p = rule.get("params", rule)
    return (cls, rule["kind"], p.get("dimension") if rule["kind"] == "target_market" else None)


def value_ok(gold, got):
    for field, want in gold["params"].items():
        if field == "dimension":
            continue
        accepted = gold.get("accept", {}).get(field, [want])
        have = got.get(field)
        if field == "allowed_categories":
            if sorted(have or []) not in [sorted(a) for a in accepted]:
                return False
        elif field == "value":
            if norm(str(have or "")) not in [norm(str(a)) for a in accepted]:
                return False
        elif field == "currency":
            if (have or "").upper() != want:
                return False
        elif have not in accepted and not (isinstance(have, (int, float)) and have in [a for a in accepted if a is not None]):
            return False
    return True


def check_gold():
    bad = 0
    for r in GOLD["rules"]:
        for c in r["citations"]:
            ok = norm(c["quote"]) in norm(PAGES[c["doc"]][c["page"] - 1])
            bad += not ok
            print(f"{'ok ' if ok else 'BAD'} {r['id']:<18} {c['doc']} p{c['page']}: {c['quote'][:70]}")
    auto = select_pages(PAGES["prospectus"])
    needed = sorted({c["page"] for r in GOLD["rules"] if not r.get("optional") for c in r["citations"] if c["doc"] == "prospectus"})
    print(f"\nautomatic scope ({len(auto)} pages) covers gold prospectus pages {needed}: missing {[p for p in needed if p not in auto]}")
    return bad


def score(run):
    got = {}
    for r in run["rules"]:
        for cls in expand(r):
            got.setdefault(key(r, cls), []).append(r)

    rows, found, correct, required = [], 0, 0, 0
    for g in GOLD["rules"]:
        if g.get("optional") or g.get("negative") or g.get("bonus"):
            continue
        gold_pages = {(c["doc"], c["page"]) for c in g["citations"]}
        for cls in expand(g):
            required += 1
            cands = got.get(key(g, cls), [])
            hit = next((c for c in cands if value_ok(g, c)), cands[0] if cands else None)
            ok = bool(hit) and value_ok(g, hit)
            found += bool(hit)
            correct += ok
            cites = hit["citations"] if hit else []
            on_gold = any((c.get("doc"), c.get("page")) in gold_pages for c in cites)
            rows.append((g["id"], cls, "found" if hit else "MISSING", "ok" if ok else ("WRONG" if hit else "-"),
                         f"{sum(c.get('verified', False) for c in cites)}/{len(cites)}", "yes" if on_gold else "no",
                         (hit or {}).get("confidence", "")))

    all_cites = [c for r in run["rules"] for c in r["citations"]]
    verified = sum(c.get("verified", False) for c in all_cites)
    uncited = [r for r in run["rules"] if not r["citations"]]

    traps = []
    for g in GOLD["rules"]:
        if g.get("negative"):
            bad = [r for cls in expand(g) for r in got.get(key(g, cls), []) if all(r.get(f) == v for f, v in g["params"].items())]
            traps.append(f"{g['id']}: {'FALSE POSITIVE' if bad else 'avoided'}")
        if g.get("bonus"):
            flagged = any(r["contradiction"] for cls in expand(g) for k, rs in got.items() if k[0] == cls for r in rs)
            traps.append(f"{g['id']}: {'flagged' if flagged else 'not flagged'}")

    gold_keys = {key(g, cls) for g in GOLD["rules"] for cls in expand(g)}
    unexpected = [r for r in run["rules"] if r["kind"] != "unmodelled_restriction"
                  and not any(key(r, cls) in gold_keys for cls in expand(r))]
    unmodelled = [r for r in run["rules"] if r["kind"] == "unmodelled_restriction"]

    out = [f"# Score: {run['label']}", "",
           f"- Scope: {len(run['selected_pages'])} prospectus pages, pass-1 input {run['pass1_input_tokens']:,} tokens",
           f"- Cost ${run['total_usd']}, latency {run['total_seconds']}s ({', '.join(f'{u['model']} {u['seconds']}s ${u['usd']}' for u in run['usage'])})",
           f"- **Rule recall {found}/{required}**, **value accuracy {correct}/{required}**",
           f"- Citation support: {verified}/{len(all_cites)} quotes verified on their page; {len(uncited)} rules with no citation",
           f"- Traps: {'; '.join(traps)}", "",
           "| gold rule | class | found | value | quotes verified | on gold page | confidence |", "|---|---|---|---|---|---|---|"]
    out += [f"| {' | '.join(str(x) for x in row)} |" for row in rows]
    if unexpected:
        out += ["", "## Unexpected rules (review by hand)"] + [f"- {r['share_class']} {r['kind']}: {r['summary']}" for r in unexpected]
    if unmodelled:
        out += ["", "## Unmodelled Restrictions extracted"] + [f"- {r['share_class']}: {r['summary']}" for r in unmodelled]
    return "\n".join(out)


if __name__ == "__main__":
    if "--check-gold" in sys.argv:
        sys.exit(check_gold())
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else max((HERE / "runs").glob("*.json"))
    report = score(json.load(open(path)))
    path.with_suffix(".md").write_text(report)
    print(report)
