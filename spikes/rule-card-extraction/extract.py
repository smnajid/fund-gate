# /// script
# dependencies = ["anthropic", "pypdf"]
# ///
"""PROTOTYPE — throwaway spike for 'Can we extract and validate a Rule Card from a real prospectus?'.

Pipeline (from the extraction + Foundry research tickets):
  0. scope: pick the pages of the 944-page umbrella prospectus that matter for one sub-fund
  1. pass 1 (Opus 5.5): PDFs with Citations on -> cited prose, one bullet per rule
  2. pass 2 (Sonnet 5.5): structured output -> Rule Card JSON, rules point at pass-1 citation ids
  3. verify: every quote must be found on the page it cites (pypdf page text)
Writes runs/<stamp>-<label>.json. Score it with score.py.

  uv run extract.py                       # automatic page selection
  uv run extract.py --pages 3,5,16,123,124,138,139 --label gold-pages
  uv run extract.py --dry-run             # scope + count_tokens only, no generation
  uv run extract.py --backend plan        # headless Claude Code on the Claude plan (no API credits)

--backend plan: one pass instead of two. A fresh `claude -p` session (Read tool only, in a temp dir
holding just the three PDFs, so it can't see the answer key) returns Rule Card JSON under --json-schema,
writing {doc, page, quote} itself instead of using the Citations API; step 3 is what catches made-up quotes.
Cost there is Claude Code's list-price estimate, not a bill.
"""
import argparse, base64, io, json, re, subprocess, tempfile, time
from pathlib import Path

import anthropic
from pypdf import PdfReader, PdfWriter

HERE = Path(__file__).parent
DOCS = HERE / "docs"
FUND = "Global Climate and Environment Fund"
CLASSES = {"BP-EUR": "LU0348926287", "BI-EUR": "LU0348927095"}
KIDS = ["kid-BP-EUR", "kid-BI-EUR"]
EXTRACT_MODEL, NORMALISE_MODEL = "claude-opus-5-5", "claude-sonnet-5-5"
# $ per MTok: input, output, cache write (5m), cache read
PRICE = {"claude-opus-5-5": (4.0, 20.0, 5.0, 0.20), "claude-sonnet-5-5": (2.0, 10.0, 2.5, 0.20)}

KEYWORDS = [r"US person", r"who can invest", r"institutional investors", r"minimum initial", r"core share class",
            r"eligib", r"Regulation S", r"qualified investor", r"well-informed", r"professional investor",
            r"not be (directly or indirectly )?offered", r"reject", r"restrict"]


def norm(s):
    s = s.replace("’", "'").replace("“", '"').replace("”", '"').replace("–", "-").replace("—", "-")
    # whitespace-free: pypdf breaks lines inside phrases and around quote marks
    return re.sub(r"\s+", "", s).lower()


def select_pages(pages, max_pages=40):
    """Deterministic scoping: the sub-fund's own description page, any page naming the sub-fund
    (fee tables), plus general pages scored by restriction keywords. Other sub-funds' description
    pages are excluded."""
    chosen, scored = set(), []
    for i, text in enumerate(pages, start=1):
        head = norm(text[:400])
        if norm(FUND) in head and "investment objective" in norm(text):
            chosen.add(i)
            continue
        if "investment objective and policy" in norm(text[:600]):  # another sub-fund's description
            continue
        if norm(FUND) in norm(text):
            chosen.add(i)
        score = sum(len(re.findall(k, text, re.I)) for k in KEYWORDS)
        if score >= 2:
            scored.append((score, i))
    for _, i in sorted(scored, reverse=True):
        if len(chosen) >= max_pages:
            break
        chosen.add(i)
    return sorted(chosen)


def sub_pdf(path, page_numbers):
    reader, writer = PdfReader(path), PdfWriter()
    for n in page_numbers:
        writer.add_page(reader.pages[n - 1])
    buf = io.BytesIO()
    writer.write(buf)
    return buf.getvalue()


def doc_block(data, title, cache=False):
    block = {"type": "document", "title": title, "citations": {"enabled": True},
             "source": {"type": "base64", "media_type": "application/pdf", "data": base64.standard_b64encode(data).decode()}}
    if cache:
        block["cache_control"] = {"type": "ephemeral"}
    return block


SYSTEM = """You extract a Rule Card from fund documentation for a private bank's fund dealing desk.
A Rule Card is the set of Hard Restrictions and Target Market criteria deciding whether a client may subscribe to one Share Class.
Every rule must be supported by a quote from the documents; cite the exact passage.

Rule kinds (use only these):
- investor_category: which investor categories may buy the class (all / retail / professional / institutional / qualified / well-informed); include how the prospectus defines the category.
- us_person_exclusion: whether US persons are excluded and which definition (Regulation S, FATCA, IRC/citizenship, mixed); say whether US citizenship alone excludes.
- minimum_initial_subscription: amount and currency (0 if none).
- mandate_only_class: class reserved to discretionary-mandate clients.
- kid_required_for_retail: whether a PRIIPs KID exists for the class.
- target_market: one rule per dimension: client_type, knowledge_experience, loss_bearing, risk_sri, horizon_years.
- unmodelled_restriction: any other restriction on who may subscribe that fits none of the kinds above.
Mark a rule's scope as fund (applies to every class) or share_class.
When two documents contradict each other on a rule, never resolve it: record each document's rule with the value that document literally states, set contradiction on it, and cite both passages. A human validator decides which one wins.
Ignore fees, investment policy, tax and operations unless they restrict who may subscribe."""


def pass1(prospectus_pdf, kid_pdfs):
    content = [doc_block(prospectus_pdf, "Nordea 1, SICAV prospectus June 2026 (selected pages)")]
    content += [doc_block(pdf, f"KID {name.removeprefix('kid-')} ({CLASSES[name.removeprefix('kid-')]})", cache=(i == len(kid_pdfs) - 1))
                for i, (name, pdf) in enumerate(kid_pdfs)]
    content.append({"type": "text", "text": f"Fund: Nordea 1 – {FUND}. Share classes: " +
                    ", ".join(f"{c} ({isin})" for c, isin in CLASSES.items()) +
                    ".\nFor each share class, list every rule as a separate bullet: kind, scope, parameter values, and the supporting quote. "
                    "Rate your confidence 0-1 for each rule."})
    params = dict(model=EXTRACT_MODEL, max_tokens=32000, system=SYSTEM,
                  output_config={"effort": "high"}, messages=[{"role": "user", "content": content}])
    return params


def serialise(message, doc_maps):
    """Turn cited text blocks into prose with [cN] markers plus a citation table mapped to original pages."""
    out, table = [], {}
    for block in message.content:
        if block.type != "text":
            continue
        out.append(block.text)
        for c in block.citations or []:
            cid = f"c{len(table) + 1}"
            doc, page_map = doc_maps[c.document_index]
            # end_page_number may be exclusive; taking it inclusively only adds a page to search
            pages = [page_map[k - 1] for k in range(c.start_page_number, min(c.end_page_number, len(page_map)) + 1)]
            table[cid] = {"doc": doc, "page": pages[0], "pages": pages, "quote": c.cited_text}
            out.append(f" [{cid}]")
    return "".join(out), table


NULLABLE = lambda t: {"anyOf": [{"type": t}, {"type": "null"}]}
RULE_SCHEMA = {
    "type": "object",
    "properties": {
        "rules": {"type": "array", "items": {
            "type": "object",
            "properties": {
                "share_class": {"type": "string", "enum": [*CLASSES, "*"]},
                "scope": {"type": "string", "enum": ["fund", "share_class"]},
                "kind": {"type": "string", "enum": ["investor_category", "us_person_exclusion", "minimum_initial_subscription",
                                                     "mandate_only_class", "kid_required_for_retail", "target_market", "unmodelled_restriction"]},
                "allowed_categories": {"anyOf": [{"type": "array", "items": {"type": "string", "enum": ["all", "retail", "professional", "institutional", "qualified", "well_informed"]}}, {"type": "null"}]},
                "us_person_definition": {"anyOf": [{"type": "string", "enum": ["reg_s", "fatca", "irc_citizenship", "mixed"]}, {"type": "null"}]},
                "excludes_us_citizens": NULLABLE("boolean"),
                "amount": NULLABLE("number"),
                "currency": NULLABLE("string"),
                "mandate_only": NULLABLE("boolean"),
                "kid_available": NULLABLE("boolean"),
                "dimension": {"anyOf": [{"type": "string", "enum": ["client_type", "knowledge_experience", "loss_bearing", "risk_sri", "horizon_years"]}, {"type": "null"}]},
                "value": NULLABLE("string"),
                "contradiction": {"type": "boolean"},
                "confidence": {"type": "number"},
                "summary": {"type": "string"},
                "citation_ids": {"type": "array", "items": {"type": "string"}},
            },
            "required": ["share_class", "scope", "kind", "allowed_categories", "us_person_definition", "excludes_us_citizens", "amount",
                         "currency", "mandate_only", "kid_available", "dimension", "value", "contradiction", "confidence", "summary", "citation_ids"],
            "additionalProperties": False,
        }},
    },
    "required": ["rules"],
    "additionalProperties": False,
}

PASS2_PROMPT = """Convert these cited extraction notes into Rule Card JSON. One object per rule per share class
(use share_class "*" for fund-scope rules that apply to every class). Fill only the fields relevant to the kind, null the rest.
Target market values: client_type in {{all, retail, professional, institutional}}; knowledge_experience in {{basic, informed, advanced}};
loss_bearing in {{no_loss, limited_loss, full_loss}}; risk_sri and horizon_years as plain numbers in a string.
citation_ids must reference the [cN] markers that support the rule; never invent ids.

<notes>
{notes}
</notes>"""


def pass2(client, notes):
    t = time.time()
    r = client.messages.create(model=NORMALISE_MODEL, max_tokens=16000,
                               output_config={"effort": "medium", "format": {"type": "json_schema", "schema": RULE_SCHEMA}},
                               messages=[{"role": "user", "content": PASS2_PROMPT.format(notes=notes)}])
    text = next(b.text for b in r.content if b.type == "text")
    return json.loads(text)["rules"], r, time.time() - t


def verify(rules, table, page_text):
    for rule in rules:
        rule["citations"] = []
        for cid in rule.pop("citation_ids"):
            c = table.get(cid)
            if not c:
                rule["citations"].append({"id": cid, "verified": False, "error": "unknown citation id"})
                continue
            text = " ".join(page_text[c["doc"]][p - 1] for p in c["pages"])
            rule["citations"].append({**c, "id": cid, "verified": norm(c["quote"]) in norm(text)})
    return rules


PLAN_DOCS = {"excerpt.pdf": "prospectus", "kid-BP-EUR.pdf": "kid-BP-EUR", "kid-BI-EUR.pdf": "kid-BI-EUR"}
PLAN_SCHEMA = json.loads(json.dumps(RULE_SCHEMA))
_rule = PLAN_SCHEMA["properties"]["rules"]["items"]
del _rule["properties"]["citation_ids"]
_rule["required"].remove("citation_ids")
_rule["properties"]["citations"] = {"type": "array", "items": {
    "type": "object", "additionalProperties": False, "required": ["file", "page", "quote"],
    "properties": {"file": {"type": "string", "enum": list(PLAN_DOCS)}, "page": {"type": "integer"}, "quote": {"type": "string"}}}}
_rule["required"].append("citations")

PLAN_PROMPT = SYSTEM + """

Files in the current directory (read them with the Read tool; PDFs over 20 pages need several reads using the pages parameter):
- excerpt.pdf: {n} selected pages of the Nordea 1, SICAV prospectus (June 2026)
- kid-BP-EUR.pdf: KID of share class BP-EUR ({bp})
- kid-BI-EUR.pdf: KID of share class BI-EUR ({bi})

Fund: Nordea 1 – {fund}. Return the Rule Card for share classes BP-EUR and BI-EUR as JSON.
One object per rule per share class (share_class "*" for fund-scope rules that apply to every class).
Fill only the fields relevant to the kind, null the rest. Target market values: client_type in {{all, retail, professional, institutional}};
knowledge_experience in {{basic, informed, advanced}}; loss_bearing in {{no_loss, limited_loss, full_loss}}; risk_sri and horizon_years as plain numbers in a string.
Every rule needs at least one citation: the file, the 1-based page number within that file, and a quote copied verbatim
(one contiguous passage, no ellipsis) from that page. Read every page before answering."""


def run_plan(prospectus_pdf, kid_pdfs, selected, model):
    work = Path(tempfile.mkdtemp(prefix="rule-card-"))
    (work / "excerpt.pdf").write_bytes(prospectus_pdf)
    for name, pdf in kid_pdfs:
        (work / f"{name}.pdf").write_bytes(pdf)
    prompt = PLAN_PROMPT.format(n=len(selected), bp=CLASSES["BP-EUR"], bi=CLASSES["BI-EUR"], fund=FUND)
    cmd = ["claude", "-p", prompt, "--model", model, "--tools", "Read", "--allowedTools", "Read",
           "--output-format", "stream-json", "--verbose", "--json-schema", json.dumps(PLAN_SCHEMA), "--no-session-persistence"]
    t, out = time.time(), None
    # stream so a stalled session is visible: print every tool call and non-assistant event as it happens
    with subprocess.Popen(cmd, cwd=work, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) as proc:
        for line in proc.stdout:
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                print(f"  [{time.time() - t:5.0f}s] {line.strip()[:200]}", flush=True)
                continue
            kind = event.get("type")
            if kind == "result":
                out = event
            elif kind == "assistant":
                for block in event["message"].get("content", []):
                    if block.get("type") == "tool_use":
                        print(f"  [{time.time() - t:5.0f}s] {block['name']} {json.dumps(block['input'])[:120]}", flush=True)
            elif kind not in ("user", "stream_event"):
                print(f"  [{time.time() - t:5.0f}s] {kind} {event.get('subtype', '')}", flush=True)
    seconds = time.time() - t
    if out is None:
        raise SystemExit("claude -p ended without a result event")
    if out.get("is_error"):
        raise SystemExit(f"claude -p failed: {out.get('result')}")
    data = out.get("structured_output") or json.loads(out["result"])
    page_maps = {"prospectus": selected}
    rules = []
    for r in data["rules"]:
        cites = []
        for c in r.pop("citations"):
            doc = PLAN_DOCS[c["file"]]
            pm = page_maps.get(doc)
            page = pm[c["page"] - 1] if pm and 1 <= c["page"] <= len(pm) else (c["page"] if not pm else None)
            cites.append({"doc": doc, "page": page, "pages": [page] if page else [], "quote": c["quote"]})
        rules.append({**r, "citations": cites})
    mu = out.get("modelUsage", {}).get(model, {})
    usage = {"model": f"{model} (claude -p)", "seconds": round(seconds, 1), "turns": out.get("num_turns"),
             "input": mu.get("inputTokens"), "output": mu.get("outputTokens"), "cache_write": mu.get("cacheCreationInputTokens"),
             "cache_read": mu.get("cacheReadInputTokens"), "usd": round(out.get("total_cost_usd", 0), 4)}
    return rules, usage


def verify_plan(rules, page_text):
    for rule in rules:
        for c in rule["citations"]:
            text = " ".join(page_text[c["doc"]][p - 1] for p in c["pages"] if 1 <= p <= len(page_text[c["doc"]]))
            c["verified"] = bool(text) and norm(c["quote"]) in norm(text)
    return rules


def cost(model, u):
    i, o, cw, cr = PRICE[model]
    return ((u.input_tokens or 0) * i + (u.output_tokens or 0) * o + (u.cache_creation_input_tokens or 0) * cw
            + (u.cache_read_input_tokens or 0) * cr) / 1e6


def usage_dict(model, u, seconds):
    return {"model": model, "seconds": round(seconds, 1), "input": u.input_tokens, "output": u.output_tokens,
            "cache_write": u.cache_creation_input_tokens, "cache_read": u.cache_read_input_tokens, "usd": round(cost(model, u), 4)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", help="comma-separated prospectus pages instead of automatic selection")
    ap.add_argument("--max-pages", type=int, default=40)
    ap.add_argument("--label", default="auto")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--backend", choices=["api", "plan"], default="api")
    a = ap.parse_args()

    page_text = {name: json.load(open(DOCS / f"{name}.pages.json")) for name in ["prospectus", *KIDS]}
    selected = [int(p) for p in a.pages.split(",")] if a.pages else select_pages(page_text["prospectus"], a.max_pages)
    print(f"scope: {len(selected)} of {len(page_text['prospectus'])} prospectus pages: {selected}")

    prospectus_pdf = sub_pdf(DOCS / "prospectus.pdf", selected)
    kid_pdfs = [(k, (DOCS / f"{k}.pdf").read_bytes()) for k in KIDS]
    doc_maps = [("prospectus", selected)] + [(k, list(range(1, len(page_text[k]) + 1))) for k in KIDS]

    if a.backend == "plan":
        rules, usage = run_plan(prospectus_pdf, kid_pdfs, selected, EXTRACT_MODEL)
        rules = verify_plan(rules, page_text)
        tokens = (usage["input"] or 0) + (usage["cache_write"] or 0) + (usage["cache_read"] or 0)
        save({"label": a.label, "backend": "plan", "selected_pages": selected, "pass1_input_tokens": tokens,
              "usage": [usage], "rules": rules})
        return

    client = anthropic.Anthropic()
    params = pass1(prospectus_pdf, kid_pdfs)
    tokens = client.messages.count_tokens(model=EXTRACT_MODEL, system=params["system"], messages=params["messages"]).input_tokens
    print(f"pass 1 input: {tokens:,} tokens (~${tokens * PRICE[EXTRACT_MODEL][0] / 1e6:.2f} uncached)")
    if a.dry_run:
        return

    t = time.time()
    with client.messages.stream(**params) as stream:
        m1 = stream.get_final_message()
    s1 = time.time() - t
    if m1.stop_reason != "end_turn":
        print(f"pass 1 stop_reason={m1.stop_reason}")
    notes, table = serialise(m1, doc_maps)
    print(f"pass 1: {s1:.0f}s, {len(table)} citations")

    rules, m2, s2 = pass2(client, notes)
    rules = verify(rules, table, page_text)
    print(f"pass 2: {s2:.0f}s, {len(rules)} rules")

    save({"label": a.label, "backend": "api", "selected_pages": selected, "pass1_input_tokens": tokens,
           "usage": [usage_dict(EXTRACT_MODEL, m1.usage, s1), usage_dict(NORMALISE_MODEL, m2.usage, s2)],
           "notes": notes, "citations": table, "rules": rules})


def save(run):
    run["total_usd"] = round(sum(u["usd"] for u in run["usage"]), 4)
    run["total_seconds"] = round(sum(u["seconds"] for u in run["usage"]), 1)
    out = HERE / "runs" / f"{time.strftime('%Y%m%d-%H%M%S')}-{run['backend']}-{run['label']}.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(run, indent=2, ensure_ascii=False))
    print(f"${run['total_usd']} in {run['total_seconds']}s -> {out.relative_to(HERE)}")


if __name__ == "__main__":
    main()
