#!/usr/bin/env bash
# PROTOTYPE, ticket #32. Resumable: run again after a VM stop. Logs to job.log, result in report.md, DONE/FAILED marker.
set -euo pipefail
cd "$(dirname "$0")"
rm -f DONE FAILED
trap 'echo "FAILED at line $LINENO" | tee FAILED' ERR
echo "== $(date) start"

# 1. Elasticsearch, single node, trial licence (RRF retriever), no security on localhost only
if ! docker ps --format '{{.Names}}' | grep -q '^fundgate-es-prototype$'; then
  if docker ps -a --format '{{.Names}}' | grep -q '^fundgate-es-prototype$'; then
    docker start fundgate-es-prototype
  else
    docker run -d --name fundgate-es-prototype -p 127.0.0.1:9200:9200 \
      -e discovery.type=single-node -e xpack.security.enabled=false \
      -e xpack.license.self_generated.type=trial -e node.store.allow_mmap=false \
      -e ES_JAVA_OPTS="-Xms3g -Xmx3g" -v fundgate-es-prototype-data:/usr/share/elasticsearch/data \
      docker.elastic.co/elasticsearch/elasticsearch:8.17.0
  fi
fi
until curl -s 127.0.0.1:9200/_cluster/health | grep -qE '"status":"(green|yellow)"'; do sleep 3; done
echo "== $(date) elasticsearch up"

# 2. Python, CPU-only torch, no pip cache (disk is tight)
if [[ ! -x .venv/bin/python ]]; then
  python3 -m venv .venv
  .venv/bin/pip install -q --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch
  .venv/bin/pip install -q --no-cache-dir sentence-transformers numpy
fi
echo "== $(date) python ready"

# 3. embed, index, retrieve, score
.venv/bin/python rag_es.py
echo "== $(date) done"
touch DONE
