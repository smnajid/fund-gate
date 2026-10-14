# PROTOTYPE, ticket #32. Page selection by retrieval in Elasticsearch: BM25, kNN (multilingual-e5-small), hybrid (RRF),
# hybrid + v2 keywords, against the Java spike's answer keys with the same page budget. Every step resumes:
# embeddings are cached in emb/, a document already indexed with the right passage count is skipped.
import json, os, re, sys, time, urllib.request

import numpy as np

ES = os.environ.get("ES", "http://127.0.0.1:9200")
INDEX = "fundeli5-passages-prototype"
MODEL = os.environ.get("MODEL", "intfloat/multilingual-e5-small")
BUDGETS = [int(b) for b in os.environ.get("BUDGETS", "40,25").split(",")]
FRONT, WIN, STRIDE, TOPN = 5, 1000, 800, 300


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def es(method, path, body=None, ndjson=False):
    data = None
    if body is not None:
        data = body.encode() if ndjson else json.dumps(body).encode()
    req = urllib.request.Request(ES + path, data=data, method=method,
                                 headers={"Content-Type": "application/x-ndjson" if ndjson else "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{method} {path}: {e.code} {e.read()[:500]!r}")


def queries(sub):
    # one English query per rule kind: the multilingual model has to bridge DE/FR documents
    return {
        "us": "Shares may not be offered, sold or transferred to US Persons; definition of US Person",
        "elig": "share classes reserved for or only available to institutional, qualified or eligible investors",
        "min": "minimum initial subscription and minimum investment amount for each share class",
        "desc": f"{sub} investment objective and investment policy",
        "fund": f"{sub} share classes, fees and minimum investment",
        "tm": "profile of the typical investor, target market, intended retail investor",
        "reject": "the board may reject subscriptions; restrictions on ownership of shares; who can invest",
    }


def passages(pages):
    out = []
    for p, t in enumerate(pages, 1):
        t = re.sub(r"\s+", " ", t).strip()
        for s in range(0, max(1, len(t) - 200), STRIDE):
            out.append((p, t[s:s + WIN]))
    return out


def pages_of(hits):
    # passage ranking -> page ranking, a page ranked by its best passage
    seen = []
    for h in hits:
        p = h["_source"]["page"]
        if p not in seen:
            seen.append(p)
    return seen


def take(rankings, n, budget, seed=()):
    # front matter, then round-robin over the per-query page rankings so every rule kind gets pages
    chosen = list(dict.fromkeys(list(range(1, min(FRONT, n) + 1)) + list(seed)))
    i = 0
    while len(chosen) < min(budget, n) and any(i < len(r) for r in rankings):
        for r in rankings:
            if i < len(r) and r[i] not in chosen and len(chosen) < budget:
                chosen.append(r[i])
        i += 1
    return set(chosen)


def hit(rule, sel):
    return rule["page"] in sel or any(a in sel for a in (rule.get("alt") or []))


def ensure_index():
    try:
        es("GET", f"/{INDEX}")
    except RuntimeError:
        es("PUT", f"/{INDEX}", {
            "settings": {"number_of_shards": 1, "number_of_replicas": 0},
            "mappings": {"properties": {
                "doc": {"type": "keyword"}, "page": {"type": "integer"},
                "text": {"type": "text", "analyzer": "standard"},
                "vec": {"type": "dense_vector", "dims": 384, "index": True, "similarity": "cosine"}}}})
        log("created index", INDEX)


def indexed(doc):
    return es("POST", f"/{INDEX}/_count", {"query": {"term": {"doc": doc}}})["count"]


def main():
    import torch
    torch.set_num_threads(int(os.environ.get("THREADS", "2")))
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(MODEL, device="cpu")
    model.max_seq_length = 320
    log("license", es("GET", "/_license")["license"]["type"])
    ensure_index()
    v2 = json.load(open("v2-selected.json"))
    keys = json.load(open("page-recall-key.json")) + json.load(open("page-recall-key-unseen.json"))
    docs = [d for d in keys if d["rules"]]
    os.makedirs("emb", exist_ok=True)

    # 1. embed + index, resumable
    for d in docs:
        doc = d["doc"]
        pages = json.load(open(f"{doc}.pages.json"))
        if sum(map(len, pages)) < 300 * len(pages):
            log("skip, no text layer:", doc)
            continue
        ps = passages(pages)
        cache = f"emb/{doc}.npy"
        if os.path.exists(cache):
            E = np.load(cache)
        else:
            t = time.time()
            E = model.encode(["passage: " + x for _, x in ps], batch_size=32, normalize_embeddings=True)
            np.save(cache, E)
            log(f"embedded {doc}: {len(ps)} passages, {len(pages)} pages, {time.time() - t:.0f}s")
        if indexed(doc) != len(ps):
            es("POST", f"/{INDEX}/_delete_by_query?refresh=true", {"query": {"term": {"doc": doc}}})
            for s in range(0, len(ps), 500):
                lines = []
                for i in range(s, min(s + 500, len(ps))):
                    lines.append(json.dumps({"index": {"_index": INDEX}}))
                    lines.append(json.dumps({"doc": doc, "page": ps[i][0], "text": ps[i][1],
                                             "vec": [round(float(x), 6) for x in E[i]]}))
                r = es("POST", "/_bulk", "\n".join(lines) + "\n", ndjson=True)
                if r.get("errors"):
                    raise RuntimeError(f"bulk errors on {doc}")
            es("POST", f"/{INDEX}/_refresh")
            log(f"indexed {doc}: {len(ps)} passages")

    # 2. retrieve per document and rule-kind query, through Elasticsearch
    rankings = {}
    for d in docs:
        doc, sub = d["doc"], d["subFund"]
        if not os.path.exists(f"emb/{doc}.npy"):
            continue
        flt = {"term": {"doc": doc}}
        r = {"bm25": [], "knn": [], "hybrid": []}
        for name, q in queries(sub).items():
            qv = [float(x) for x in model.encode("query: " + q, normalize_embeddings=True)]
            text_q = {"bool": {"must": {"match": {"text": q}}, "filter": flt}}
            knn = {"field": "vec", "query_vector": qv, "k": TOPN, "num_candidates": 1000, "filter": flt}
            src = {"_source": ["page"], "size": TOPN}
            r["bm25"].append(pages_of(es("POST", f"/{INDEX}/_search", {**src, "query": text_q})["hits"]["hits"]))
            r["knn"].append(pages_of(es("POST", f"/{INDEX}/_search", {**src, "knn": knn})["hits"]["hits"]))
            r["hybrid"].append(pages_of(es("POST", f"/{INDEX}/_search", {**src, "retriever": {"rrf": {
                "retrievers": [{"standard": {"query": text_q}}, {"knn": knn}],
                "rank_window_size": TOPN, "rank_constant": 60}}})["hits"]["hits"]))
        rankings[doc] = r
    json.dump(rankings, open("rankings.json", "w"))
    log("retrieval done for", len(rankings), "documents")

    # 3. score every method at every budget
    out = [f"# Page selection by retrieval (ticket #32, PROTOTYPE)\n",
           f"Elasticsearch {es('GET', '/')['version']['number']}, embeddings `{MODEL}`, passages {WIN} chars "
           f"(stride {STRIDE}), page = best passage, front matter pages 1-{FRONT} always sent, round-robin over "
           f"{len(queries('x'))} rule-kind queries. v1 (Nordea-tuned keywords, measured in Java): unseen 40/74, "
           f"demo 35/35.\n"]
    methods = ["v2", "bm25", "knn", "hybrid", "hybrid+v2"]
    demo = {x["doc"] for x in json.load(open("page-recall-key.json"))}
    for budget in BUDGETS:
        tot = {(m, s): [0, 0, 0] for m in methods for s in ("demo", "unseen")}
        rows = []
        for d in docs:
            doc = d["doc"]
            if doc not in rankings:
                continue
            n = len(json.load(open(f"{doc}.pages.json")))
            r = rankings[doc]
            v = v2.get(doc, [])
            sel = {"v2": set(v), "bm25": take(r["bm25"], n, budget), "knn": take(r["knn"], n, budget),
                   "hybrid": take(r["hybrid"], n, budget),
                   "hybrid+v2": take(r["hybrid"], n, max(budget, len(v)), seed=v)}
            setname = "demo" if doc in demo else "unseen"
            cells = []
            for m in methods:
                h = sum(hit(x, sel[m]) for x in d["rules"])
                t = tot[(m, setname)]
                t[0] += h; t[1] += len(d["rules"]); t[2] += len(sel[m])
                miss = [x["kind"] for x in d["rules"] if not hit(x, sel[m])]
                cells.append(f"{h}/{len(d['rules'])} ({len(sel[m])}p)" + (f" ✗ {', '.join(miss)}" if miss else ""))
            rows.append(f"| {doc} | {n} | " + " | ".join(cells) + " |")
        out.append(f"\n## Budget {budget} pages\n")
        out.append("| set | " + " | ".join(methods) + " |")
        out.append("|---|" + "---|" * len(methods))
        for s in ("demo", "unseen"):
            out.append(f"| {s} | " + " | ".join(
                f"**{tot[(m, s)][0]}/{tot[(m, s)][1]}**, {tot[(m, s)][2]} pages" for m in methods) + " |")
        out.append("\n| document | pages | " + " | ".join(methods) + " |")
        out.append("|---|---|" + "---|" * len(methods))
        out.extend(rows)
    open("report.md", "w").write("\n".join(out) + "\n")
    log("report.md written")


if __name__ == "__main__":
    main()
